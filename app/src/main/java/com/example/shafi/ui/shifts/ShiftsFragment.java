package com.example.shafi.ui.shifts;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.R;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.FragmentShiftsBinding;
import com.example.shafi.ui.ShiftAdapter;
import com.example.shafi.ui.screens.ShiftDetailActivity;
import com.example.shafi.viewmodel.ShiftsViewModel;

import java.util.ArrayList;
import java.util.List;

public class ShiftsFragment extends Fragment {
    private FragmentShiftsBinding binding;
    private ShiftsViewModel viewModel;
    private String filter = "UPCOMING";
    private List<Dtos.ShiftItem> all = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentShiftsBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ShiftsViewModel.class);
        ShiftAdapter adapter = new ShiftAdapter(item ->
                startActivity(new Intent(requireContext(), ShiftDetailActivity.class).putExtra("id", item.id)));
        binding.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvItems.setAdapter(adapter);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.chipUpcoming.setOnClickListener(v -> choose(binding.chipUpcoming, "UPCOMING", adapter));
        binding.chipActive.setOnClickListener(v -> choose(binding.chipActive, "ACTIVE", adapter));
        binding.chipCompleted.setOnClickListener(v -> choose(binding.chipCompleted, "COMPLETED", adapter));
        binding.chipCancelled.setOnClickListener(v -> choose(binding.chipCancelled, "CANCELLED", adapter));
        viewModel.shifts().observe(getViewLifecycleOwner(), resource -> {
            binding.progress.setVisibility(resource.state == Resource.State.LOADING ? View.VISIBLE : View.GONE);
            binding.refresh.setRefreshing(false);
            if (resource.data != null) all = resource.data;
            if (resource.state == Resource.State.ERROR && all.isEmpty()) {
                binding.tvState.setText(resource.message);
                binding.tvState.setVisibility(View.VISIBLE);
            }
            show(adapter);
        });
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void choose(TextView selected, String next, ShiftAdapter adapter) {
        filter = next;
        TextView[] chips = {binding.chipUpcoming, binding.chipActive, binding.chipCompleted, binding.chipCancelled};
        for (TextView chip : chips) {
            chip.setBackgroundResource(chip == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(requireContext().getColor(chip == selected ? R.color.navy_action : R.color.text_secondary));
        }
        show(adapter);
    }

    private void show(ShiftAdapter adapter) {
        List<Dtos.ShiftItem> visible = new ArrayList<>();
        for (Dtos.ShiftItem item : all) {
            if (filter.equals(item.status)) visible.add(item);
        }
        adapter.submitList(visible);
        boolean showEmpty = visible.isEmpty() && binding.progress.getVisibility() != View.VISIBLE;
        binding.tvState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) binding.tvState.setText("No " + filter.toLowerCase() + " shifts.");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
