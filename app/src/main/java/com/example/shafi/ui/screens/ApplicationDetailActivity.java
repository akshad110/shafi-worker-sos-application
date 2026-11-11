package com.example.shafi.ui.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.R;
import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityApplicationBinding;
import com.example.shafi.databinding.RowDetailBinding;
import com.example.shafi.ui.AppDialog;

public class ApplicationDetailActivity extends AppCompatActivity {
    private ActivityApplicationBinding binding;
    private WorkerRepository repository;
    private Dtos.ApplicationItem item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityApplicationBinding.inflate(getLayoutInflater());
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
        binding.tvError.setVisibility(View.GONE);
        Dtos.JobItem job = loaded.job;
        binding.tvKicker.setText(job == null || job.hospitalName == null ? "Application" : job.hospitalName);
        binding.tvTitle.setText(job == null || job.title == null ? "Application" : job.title);
        binding.tvStatus.setText(Format.status(loaded.status));
        row(binding.rowReference, R.drawable.ic_document, "Reference", loaded.reference);
        row(binding.rowShift, R.drawable.ic_calendar, "Shift", job == null ? "" : Format.shift(Format.prettyDate(job.shiftDate), job.startTime, job.endTime));
        row(binding.rowPay, R.drawable.ic_earnings, "Pay", job == null ? "" : Format.money(job.compensationMinor, job.currency) + " " + Format.payUnit(job.paymentUnit));
        row(binding.rowUpdated, R.drawable.ic_edit, "Updated", Format.prettyStamp(loaded.updatedAt));
        StringBuilder history = new StringBuilder();
        if (loaded.history != null) {
            for (Dtos.HistoryItem step : loaded.history) {
                if (history.length() > 0) history.append("\n\n");
                history.append(Format.status(step.status));
                if (step.at != null && !step.at.isEmpty()) history.append("  ·  ").append(Format.prettyStamp(step.at));
                if (step.note != null && !step.note.isEmpty()) history.append("\n").append(step.note);
            }
        }
        binding.tvHistory.setText(history.length() == 0 ? "No updates yet." : history.toString());
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

    private void row(RowDetailBinding row, int icon, String label, String value) {
        row.ivIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.trim().isEmpty() ? "—" : value.trim());
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message == null ? "Couldn't open this application." : message);
    }
}
