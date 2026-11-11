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
import com.example.shafi.R;
import com.example.shafi.core.Format;
import com.example.shafi.core.PayRules;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityShiftInfoBinding;
import com.example.shafi.databinding.RowDetailBinding;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class ShiftDetailActivity extends AppCompatActivity {
    private static final int LOCATION = 44;
    private ActivityShiftInfoBinding binding;
    private WorkerRepository repository;
    private Dtos.ShiftItem shift;
    private boolean checkingIn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShiftInfoBinding.inflate(getLayoutInflater());
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
                Dtos.ShiftItem cached = null;
                for (Dtos.ShiftItem item : repository.cachedShifts()) {
                    if (id.equals(item.id)) {
                        cached = item;
                        break;
                    }
                }
                Dtos.ShiftItem fallback = cached;
                runOnUiThread(() -> {
                    if (fallback != null) show(fallback);
                    else fail(error.getMessage());
                });
            }
        });
    }

    private void show(Dtos.ShiftItem item) {
        shift = item;
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.GONE);
        Dtos.JobItem job = item.job;
        String hospital = item.hospitalName != null && !item.hospitalName.isEmpty()
                ? item.hospitalName
                : (job == null || job.hospitalName == null ? "Shift" : job.hospitalName);
        String role = job != null && job.healthcareRole != null && !job.healthcareRole.isEmpty() && !"Shift".equals(job.healthcareRole)
                ? job.healthcareRole
                : (job != null && job.title != null && !job.title.isEmpty() && !"Shift".equals(job.title) ? job.title : hospital);
        binding.tvKicker.setText(hospital);
        binding.tvTitle.setText(role);
        binding.tvStatus.setText(Format.status(item.status));
        PayRules pay = PayRules.of(item);
        binding.tvPayLabel.setText(pay.recorded ? "Payment recorded" : "Payment not recorded");
        binding.tvPayAmount.setText(pay.recorded ? Format.money(pay.amountMinor, pay.currency) : "—");
        binding.tvPayNote.setText(pay.detail);
        String day = Format.shiftDay(item);
        String start = item.startTime != null && !item.startTime.isEmpty() ? item.startTime : (job == null ? "" : job.startTime);
        String end = item.endTime != null && !item.endTime.isEmpty() ? item.endTime : (job == null ? "" : job.endTime);
        row(binding.rowWhen, R.drawable.ic_calendar, "When", Format.shift(Format.prettyDate(day), start, end));
        String department = item.department != null && !item.department.isEmpty() ? item.department : (job == null ? "" : job.department);
        String address = item.address != null && !item.address.isEmpty() ? item.address : (job == null ? "" : job.address);
        row(binding.rowPlace, R.drawable.ic_home_black_24dp, "Where", (department == null ? "" : department) + (address == null || address.isEmpty() ? "" : (department == null || department.isEmpty() ? "" : " · ") + address));
        row(binding.rowAttendance, R.drawable.ic_document, "Attendance", pay.mark);
        String place = address == null ? "" : address;
        binding.btnSecondary.setVisibility(place.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnSecondary.setText("Open map");
        binding.btnSecondary.setOnClickListener(v -> openMap());
        boolean ownShift = item.startAt != null && !item.startAt.isEmpty();
        if (!ownShift) {
            binding.btnPrimary.setVisibility(View.GONE);
        } else if ("UPCOMING".equals(item.status)) {
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

    private void row(RowDetailBinding row, int icon, String label, String value) {
        row.ivIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.trim().isEmpty() ? "—" : value.trim());
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
        if (shift == null) return;
        String address = shift.address != null && !shift.address.isEmpty() ? shift.address : (shift.job == null ? "" : shift.job.address);
        String city = shift.city != null && !shift.city.isEmpty() ? shift.city : (shift.job == null ? "" : shift.job.city);
        if ((address == null || address.isEmpty()) && (city == null || city.isEmpty())) return;
        Uri uri;
        if (shift.job != null && shift.job.latitude != null && shift.job.longitude != null) {
            uri = Uri.parse("geo:" + shift.job.latitude + "," + shift.job.longitude + "?q=" + Uri.encode(address));
        } else {
            uri = Uri.parse("geo:0,0?q=" + Uri.encode((address == null ? "" : address) + " " + (city == null ? "" : city)));
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
