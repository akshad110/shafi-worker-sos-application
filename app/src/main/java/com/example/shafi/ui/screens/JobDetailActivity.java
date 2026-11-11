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

public class JobDetailActivity extends AppCompatActivity {
    private ActivityRecordBinding binding;
    private WorkerRepository repository;
    private Dtos.JobItem job;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnPrimary.setOnClickListener(v -> confirmApply());
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        String id = getIntent().getStringExtra("id");
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.JobItem item = repository.job(id);
                runOnUiThread(() -> show(item));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void show(Dtos.JobItem item) {
        job = item;
        binding.progress.setVisibility(View.GONE);
        binding.tvKicker.setText(item.hospitalName + (item.hospitalVerified ? " · Verified hospital" : ""));
        binding.tvTitle.setText(item.title);
        binding.tvStatus.setText(item.urgent ? "Urgent staffing" : "Open shift");
        String body = Format.block("Role", item.healthcareRole + " · " + item.department)
                + Format.block("When", Format.shift(item.shiftDate, item.startTime, item.endTime))
                + Format.block("Where", (item.address == null ? "" : item.address + ", ") + item.city)
                + Format.block("Pay", Format.money(item.compensationMinor, item.currency) + " " + Format.payUnit(item.paymentUnit))
                + Format.block("Vacancies", item.filledCount + " filled of " + item.vacancies)
                + Format.block("Deadline", item.deadline)
                + Format.block("Description", item.description)
                + Format.block("Reporting", item.reportingInstructions)
                + Format.bullets("Qualifications", item.qualifications)
                + Format.bullets("Skills", item.skills)
                + Format.bullets("Certifications", item.certifications)
                + Format.block("Experience", item.experienceYears == null ? "" : item.experienceYears + " years");
        binding.tvBody.setText(body.trim());
        binding.btnPrimary.setVisibility(View.VISIBLE);
        if (item.hasApplied) {
            binding.btnPrimary.setText("Already applied");
            binding.btnPrimary.setEnabled(false);
        } else if (!item.eligible) {
            binding.btnPrimary.setText("Apply");
            binding.btnPrimary.setEnabled(false);
            binding.tvError.setVisibility(View.VISIBLE);
            binding.tvError.setText(item.eligibilityReason == null ? "You cannot apply for this job yet." : item.eligibilityReason);
        } else {
            binding.btnPrimary.setText("Apply now");
            binding.btnPrimary.setEnabled(true);
        }
    }

    private void confirmApply() {
        if (job == null) return;
        Intent intent = new Intent(this, ApplyActivity.class);
        intent.putExtra("id", job.id);
        if (job.title != null) intent.putExtra("title", job.title);
        startActivity(intent);
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }
}
