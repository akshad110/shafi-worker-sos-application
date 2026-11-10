package com.example.shafi.ui.screens;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityRecordBinding;
import com.example.shafi.ui.AppDialog;

public class PlacementActivity extends AppCompatActivity {
    private ActivityRecordBinding binding;
    private WorkerRepository repository;
    private Dtos.PlacementItem item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.PlacementItem loaded = repository.placement(getIntent().getStringExtra("id"));
                runOnUiThread(() -> show(loaded));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void show(Dtos.PlacementItem loaded) {
        item = loaded;
        binding.progress.setVisibility(View.GONE);
        Dtos.JobItem job = loaded.job;
        binding.tvKicker.setText("Placement offer");
        binding.tvTitle.setText(job == null ? "Offer" : job.title);
        binding.tvStatus.setText(Format.status(loaded.status));
        String body = Format.block("Hospital", job == null ? "" : job.hospitalName)
                + Format.block("Department", job == null ? "" : job.department)
                + Format.block("Shift", job == null ? "" : Format.shift(job.shiftDate, job.startTime, job.endTime))
                + Format.block("Pay", Format.money(loaded.compensationMinor, loaded.currency))
                + Format.block("Reporting", job == null ? loaded.instructions : job.address + "\n" + loaded.instructions)
                + Format.block("Offer expires", loaded.expiresAt);
        binding.tvBody.setText(body.trim());
        boolean open = "OFFERED".equals(loaded.status);
        binding.btnPrimary.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.btnSecondary.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText("Accept offer");
        binding.btnSecondary.setText("Decline offer");
        binding.btnPrimary.setOnClickListener(v -> decide(true));
        binding.btnSecondary.setOnClickListener(v -> decide(false));
    }

    private void decide(boolean accept) {
        AppDialog.confirm(this, accept ? "Accept this offer" : "Decline this offer",
                accept ? "Accepting confirms the assignment if a vacancy is still open." : "Declining closes this offer.",
                accept ? "Accept" : "Decline", () -> {
                    binding.progress.setVisibility(View.VISIBLE);
                    AppExecutors.IO.execute(() -> {
                        try {
                            Dtos.PlacementItem next = accept ? repository.accept(item.id) : repository.decline(item.id);
                            runOnUiThread(() -> {
                                show(next);
                                if (accept && "ACCEPTED".equals(next.status)) {
                                    AppDialog.note(this, "Shift confirmed", "The assignment is in My Shifts. Open it there to check in when it is time.");
                                }
                            });
                        } catch (Exception error) {
                            runOnUiThread(() -> fail(error.getMessage()));
                        }
                    });
                });
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }
}
