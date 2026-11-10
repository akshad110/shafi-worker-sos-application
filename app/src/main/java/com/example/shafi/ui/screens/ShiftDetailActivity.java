package com.example.shafi.ui.screens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityRecordBinding;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class ShiftDetailActivity extends AppCompatActivity {
    private static final int LOCATION = 44;
    private ActivityRecordBinding binding;
    private WorkerRepository repository;
    private Dtos.ShiftItem shift;
    private boolean checkingIn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());
        String id = getIntent().getStringExtra("id");
        if (id == null || id.isEmpty()) {
            finish();
            return;
        }
        load(id);
    }

    private void load(String id) {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ShiftItem item = repository.shift(id);
                runOnUiThread(() -> show(item));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void show(Dtos.ShiftItem item) {
        shift = item;
        binding.progress.setVisibility(View.GONE);
        Dtos.JobItem job = item.job;
        binding.tvKicker.setText(job == null ? "Shift" : job.hospitalName);
        binding.tvTitle.setText(job == null ? "Shift" : job.title);
        binding.tvStatus.setText(Format.status(item.status));
        String pay = item.payment == null ? "Payment is recorded only after the hospital confirms it."
                : Format.status(item.payment.status) + " · " + Format.money(item.payment.amountMinor, item.payment.currency)
                + (item.payment.reference == null || item.payment.reference.isEmpty() ? "" : "\nRef " + item.payment.reference);
        String body = Format.block("When", job == null ? "" : Format.shift(job.shiftDate, job.startTime, job.endTime))
                + Format.block("Department", job == null ? "" : job.department)
                + Format.block("Address", job == null ? "" : job.address)
                + Format.block("Reporting", job == null ? "" : job.reportingInstructions)
                + Format.block("Supervisor", job == null || job.supervisorName == null ? "" : job.supervisorName + " " + job.supervisorPhone)
                + Format.block("Check-in", item.checkIn == null ? "Not checked in" : item.checkIn.at)
                + Format.block("Check-out", item.checkOut == null ? "Not checked out" : item.checkOut.at)
                + Format.block("Payment", pay);
        binding.tvBody.setText(body.trim());
        binding.btnSecondary.setVisibility(View.VISIBLE);
        binding.btnSecondary.setText("Open map");
        binding.btnSecondary.setOnClickListener(v -> openMap());
        if ("UPCOMING".equals(item.status)) {
            binding.btnPrimary.setVisibility(View.VISIBLE);
            binding.btnPrimary.setText("Check in");
            binding.btnPrimary.setOnClickListener(v -> begin(true));
        } else if ("ACTIVE".equals(item.status)) {
            binding.btnPrimary.setVisibility(View.VISIBLE);
            binding.btnPrimary.setText("Check out");
            binding.btnPrimary.setOnClickListener(v -> begin(false));
        } else if ("COMPLETED".equals(item.status)) {
            binding.btnPrimary.setVisibility(View.VISIBLE);
            binding.btnPrimary.setText("Feedback");
            binding.btnPrimary.setOnClickListener(v -> feedback());
        } else {
            binding.btnPrimary.setVisibility(View.GONE);
        }
    }

    private void begin(boolean checkIn) {
        checkingIn = checkIn;
        if (shift.job != null && shift.job.geofenceEnabled
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION);
            return;
        }
        sendLocation();
    }

    private void sendLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            submit(null, null);
            return;
        }
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> submit(location == null ? null : location.getLatitude(), location == null ? null : location.getLongitude()))
                .addOnFailureListener(error -> submit(null, null));
    }

    private void submit(Double lat, Double lng) {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ShiftItem next = checkingIn ? repository.checkIn(shift.id, lat, lng) : repository.checkOut(shift.id, lat, lng);
                runOnUiThread(() -> show(next));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void openMap() {
        if (shift == null || shift.job == null) return;
        Uri uri;
        if (shift.job.latitude != null && shift.job.longitude != null) {
            uri = Uri.parse("geo:" + shift.job.latitude + "," + shift.job.longitude + "?q=" + Uri.encode(shift.job.address));
        } else {
            uri = Uri.parse("geo:0,0?q=" + Uri.encode(shift.job.address + " " + shift.job.city));
        }
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void feedback() {
        EditText input = new EditText(this);
        input.setHint("How was the shift?");
        new AlertDialog.Builder(this)
                .setTitle("Shift feedback")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> AppExecutors.IO.execute(() -> {
                    try {
                        Dtos.ShiftItem next = repository.feedback(shift.id, 5, input.getText().toString().trim());
                        runOnUiThread(() -> show(next));
                    } catch (Exception error) {
                        runOnUiThread(() -> fail(error.getMessage()));
                    }
                }))
                .setNeutralButton("Report incident", (dialog, which) -> AppExecutors.IO.execute(() -> {
                    try {
                        String note = input.getText().toString().trim();
                        Dtos.ShiftItem next = repository.incident(shift.id, "FACILITY", note.isEmpty() ? "Incident reported" : note);
                        runOnUiThread(() -> show(next));
                    } catch (Exception error) {
                        runOnUiThread(() -> fail(error.getMessage()));
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION) sendLocation();
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }
}
