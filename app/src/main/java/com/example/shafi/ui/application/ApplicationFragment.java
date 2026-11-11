package com.example.shafi.ui.application;

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
import com.example.shafi.databinding.FragmentApplicationBinding;
import com.example.shafi.ui.ApplicationAdapter;
import com.example.shafi.ui.screens.ApplicationDetailActivity;
import com.example.shafi.viewmodel.ApplicationsViewModel;

import java.util.ArrayList;
import java.util.List;

public class ApplicationFragment extends Fragment {
    private FragmentApplicationBinding binding;
    private ApplicationsViewModel viewModel;
    private String filter = "ALL";
    private List<Dtos.ApplicationItem> all = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentApplicationBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ApplicationsViewModel.class);
        ApplicationAdapter adapter = new ApplicationAdapter(item ->
                startActivity(new Intent(requireContext(), ApplicationDetailActivity.class).putExtra("id", item.id)));
        binding.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvItems.setAdapter(adapter);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.chipAll.setOnClickListener(v -> choose(binding.chipAll, "ALL", adapter));
        binding.chipReview.setOnClickListener(v -> choose(binding.chipReview, "REVIEW", adapter));
        binding.chipShort.setOnClickListener(v -> choose(binding.chipShort, "SHORTLISTED", adapter));
        binding.chipOffer.setOnClickListener(v -> choose(binding.chipOffer, "OFFERED", adapter));
        binding.chipRejected.setOnClickListener(v -> choose(binding.chipRejected, "REJECTED", adapter));
        binding.chipWithdrawn.setOnClickListener(v -> choose(binding.chipWithdrawn, "WITHDRAWN", adapter));
        viewModel.applications().observe(getViewLifecycleOwner(), resource -> {
            boolean waiting = resource.state == Resource.State.LOADING && (resource.data == null || resource.data.isEmpty());
            binding.progress.setVisibility(waiting ? View.VISIBLE : View.GONE);
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

    private void choose(TextView selected, String next, ApplicationAdapter adapter) {
        filter = next;
        TextView[] chips = {binding.chipAll, binding.chipReview, binding.chipShort, binding.chipOffer, binding.chipRejected, binding.chipWithdrawn};
        for (TextView chip : chips) {
            chip.setBackgroundResource(chip == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(requireContext().getColor(chip == selected ? R.color.navy_action : R.color.text_secondary));
        }
        show(adapter);
    }

    private void show(ApplicationAdapter adapter) {
        List<Dtos.ApplicationItem> visible = new ArrayList<>();
        for (Dtos.ApplicationItem item : all) {
            if (matches(item.status)) visible.add(item);
        }
        adapter.submitList(visible);
        binding.tvState.setVisibility(visible.isEmpty() && binding.progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        if (visible.isEmpty()) binding.tvState.setText("No applications in this list.");
    }

    private boolean matches(String status) {
        if ("ALL".equals(filter)) return true;
        if ("REVIEW".equals(filter)) return "SUBMITTED".equals(status) || "UNDER_REVIEW".equals(status);
        if ("REJECTED".equals(filter)) return "REJECTED".equals(status) || "DECLINED".equals(status) || "OFFER_EXPIRED".equals(status);
        return filter.equals(status);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
