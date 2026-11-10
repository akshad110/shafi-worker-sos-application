package com.example.shafi.ui.screens;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityListBinding;
import com.example.shafi.ui.ActivityAdapter;

import java.util.ArrayList;
import java.util.List;

public class EarningsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityListBinding binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        binding.tvTitle.setText("Earnings");
        binding.tvSummary.setText("Paid amounts appear only after a confirmed payment.");
        binding.btnBack.setOnClickListener(v -> finish());
        ActivityAdapter adapter = new ActivityAdapter(item -> {});
        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(adapter);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.Earnings earnings = new WorkerRepository(this).earnings();
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    binding.tvSummary.setText("Paid " + Format.money(earnings.totalPaidMinor, earnings.currency)
                            + " · Pending " + Format.money(earnings.totalPendingMinor, earnings.currency));
                    List<Dtos.ActivityItem> rows = new ArrayList<>();
                    if (earnings.items != null) {
                        for (Dtos.PaymentInfo payment : earnings.items) {
                            Dtos.ActivityItem row = new Dtos.ActivityItem();
                            row.id = payment.id;
                            row.title = payment.hospitalName + " · " + payment.title;
                            row.body = Format.status(payment.status) + " · " + Format.money(payment.amountMinor, payment.currency)
                                    + (payment.reference == null || payment.reference.isEmpty() ? "" : " · " + payment.reference);
                            row.read = true;
                            rows.add(row);
                        }
                    }
                    adapter.submitList(rows);
                    binding.tvState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.tvState.setText("No payments yet. Completing a shift does not mark it paid.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    binding.tvState.setVisibility(View.VISIBLE);
                    binding.tvState.setText(error.getMessage());
                });
            }
        });
    }
}
