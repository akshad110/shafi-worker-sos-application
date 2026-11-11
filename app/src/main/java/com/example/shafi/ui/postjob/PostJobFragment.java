package com.example.shafi.ui.postjob;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.R;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.FragmentPostJobBinding;
import com.example.shafi.ui.JobAdapter;
import com.example.shafi.viewmodel.JobsViewModel;

import java.util.List;

public class PostJobFragment extends Fragment {
    private FragmentPostJobBinding binding;
    private JobsViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private String shiftType = "";
    private boolean urgent;
    private String sort = "newest";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPostJobBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(JobsViewModel.class);
        JobAdapter adapter = new JobAdapter();
        binding.rvJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvJobs.setAdapter(adapter);
        binding.rvJobs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) viewModel.loadMore();
            }
        });
        binding.refresh.setOnRefreshListener(this::reload);
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (pending != null) handler.removeCallbacks(pending);
                pending = PostJobFragment.this::reload;
                handler.postDelayed(pending, 350);
            }
        });
        binding.chipAll.setOnClickListener(v -> select(binding.chipAll, "", false, "newest"));
        binding.chipUrgent.setOnClickListener(v -> select(binding.chipUrgent, "", true, "newest"));
        binding.chipDay.setOnClickListener(v -> select(binding.chipDay, "DAY", false, "newest"));
        binding.chipNight.setOnClickListener(v -> select(binding.chipNight, "NIGHT", false, "newest"));
        binding.chipPay.setOnClickListener(v -> select(binding.chipPay, "", false, "compensation"));
        viewModel.jobs().observe(getViewLifecycleOwner(), resource -> render(adapter, resource));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void select(TextView selected, String shift, boolean urgentOnly, String nextSort) {
        shiftType = shift;
        urgent = urgentOnly;
        sort = nextSort;
        TextView[] chips = {binding.chipAll, binding.chipUrgent, binding.chipDay, binding.chipNight, binding.chipPay};
        for (TextView chip : chips) {
            chip.setBackgroundResource(chip == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(requireContext().getColor(chip == selected ? R.color.navy_action : R.color.text_secondary));
        }
        reload();
    }

    private void reload() {
        if (binding == null) return;
        viewModel.search(binding.etSearch.getText().toString(), shiftType, urgent, sort);
    }

    private void render(JobAdapter adapter, Resource<List<Dtos.JobItem>> resource) {
        boolean waiting = resource.state == Resource.State.LOADING && (resource.data == null || resource.data.isEmpty());
        binding.progress.setVisibility(waiting ? View.VISIBLE : View.GONE);
        binding.refresh.setRefreshing(false);
        if (resource.data != null) adapter.submitList(resource.data);
        if (resource.state == Resource.State.ERROR) {
            binding.tvState.setText(resource.message);
            binding.tvState.setVisibility(View.VISIBLE);
        } else if (resource.state == Resource.State.SUCCESS && (resource.data == null || resource.data.isEmpty())) {
            binding.tvState.setText(resource.offline ? resource.message : "No open jobs match this search.");
            binding.tvState.setVisibility(View.VISIBLE);
        } else {
            binding.tvState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pending != null) handler.removeCallbacks(pending);
        binding = null;
    }
}
