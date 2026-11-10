package com.example.shafi.ui.screens;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityListBinding;
import com.example.shafi.ui.ActivityAdapter;
import com.example.shafi.ui.Routes;

public class NotificationsActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private WorkerRepository repository;
    private ActivityAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.tvTitle.setText("Notifications");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAction.setVisibility(View.VISIBLE);
        binding.btnAction.setText("Mark all read");
        binding.btnAction.setOnClickListener(v -> AppExecutors.IO.execute(() -> {
            try {
                repository.markAll();
                load();
            } catch (Exception error) {
                runOnUiThread(() -> show(error.getMessage(), null));
            }
        }));
        adapter = new ActivityAdapter(item -> {
            AppExecutors.IO.execute(() -> {
                try { repository.markRead(item.id); } catch (Exception ignored) {}
            });
            Routes.open(this, item.data);
        });
        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(adapter);
        load();
    }

    private void load() {
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.NoticePage page = repository.notifications();
                runOnUiThread(() -> show(null, page));
            } catch (Exception error) {
                runOnUiThread(() -> show(error.getMessage(), null));
            }
        });
    }

    private void show(String error, Dtos.NoticePage page) {
        binding.progress.setVisibility(View.GONE);
        if (error != null) {
            binding.tvState.setVisibility(View.VISIBLE);
            binding.tvState.setText(error);
            return;
        }
        int unread = page == null ? 0 : page.unread;
        binding.tvSummary.setText(unread + " unread");
        adapter.submitList(page == null ? null : page.items);
        boolean empty = page == null || page.items == null || page.items.isEmpty();
        binding.tvState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvState.setText("No notifications yet.");
    }
}
