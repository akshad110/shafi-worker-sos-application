package com.example.shafi.ui.screens;

import android.content.Intent;
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

public class ApplicationDetailActivity extends AppCompatActivity {
    private ActivityRecordBinding binding;
    private WorkerRepository repository;
    private Dtos.ApplicationItem item;

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
                Dtos.ApplicationItem loaded = repository.application(getIntent().getStringExtra("id"));
                runOnUiThread(() -> show(loaded));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void show(Dtos.ApplicationItem loaded) {
        item = loaded;
        binding.progress.setVisibility(View.GONE);
        Dtos.JobItem job = loaded.job;
        binding.tvKicker.setText(job == null ? "Application" : job.hospitalName);
        binding.tvTitle.setText(job == null ? "Application" : job.title);
        binding.tvStatus.setText(Format.status(loaded.status));
        StringBuilder history = new StringBuilder();
        if (loaded.history != null) {
            for (Dtos.HistoryItem step : loaded.history) {
                history.append(Format.status(step.status));
                if (step.note != null && !step.note.isEmpty()) history.append(" — ").append(step.note);
                history.append("\n");
            }
        }
        String body = Format.block("Reference", loaded.reference)
                + Format.block("Shift", job == null ? "" : Format.shift(job.shiftDate, job.startTime, job.endTime))
                + Format.block("Pay", job == null ? "" : Format.money(job.compensationMinor, job.currency))
                + Format.block("Updated", loaded.updatedAt)
                + Format.block("Status history", history.toString().trim());
        binding.tvBody.setText(body.trim());
        boolean offered = "OFFERED".equals(loaded.status) && loaded.placementId != null;
        binding.btnPrimary.setVisibility(offered ? View.VISIBLE : View.GONE);
        binding.btnPrimary.setText("Review offer");
        binding.btnPrimary.setOnClickListener(v -> startActivity(new Intent(this, PlacementActivity.class).putExtra("id", item.placementId)));
        boolean canWithdraw = "SUBMITTED".equals(loaded.status) || "UNDER_REVIEW".equals(loaded.status) || "SHORTLISTED".equals(loaded.status);
        binding.btnSecondary.setVisibility(canWithdraw ? View.VISIBLE : View.GONE);
        binding.btnSecondary.setText("Withdraw");
        binding.btnSecondary.setOnClickListener(v -> AppDialog.confirm(this, "Withdraw application",
                "The hospital will no longer consider this application.", "Withdraw", () -> {
                    binding.progress.setVisibility(View.VISIBLE);
                    AppExecutors.IO.execute(() -> {
                        try {
                            Dtos.ApplicationItem next = repository.withdraw(item.id);
                            runOnUiThread(() -> show(next));
                        } catch (Exception error) {
                            runOnUiThread(() -> fail(error.getMessage()));
                        }
                    });
                }));
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }
}
