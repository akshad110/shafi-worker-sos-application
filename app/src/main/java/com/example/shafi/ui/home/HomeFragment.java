package com.example.shafi.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.core.Format;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.FragmentHomeBinding;
import com.example.shafi.ui.ActivityAdapter;
import com.example.shafi.ui.JobAdapter;
import com.example.shafi.ui.Routes;
import com.example.shafi.ui.screens.JobDetailActivity;
import com.example.shafi.ui.screens.OnboardingActivity;
import com.example.shafi.ui.screens.ShiftDetailActivity;
import com.example.shafi.viewmodel.HomeDashViewModel;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeDashViewModel viewModel;
    private JobAdapter jobs;
    private ActivityAdapter activity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(HomeDashViewModel.class);
        jobs = new JobAdapter(job -> startActivity(new Intent(requireContext(), JobDetailActivity.class).putExtra("id", job.id)));
        activity = new ActivityAdapter(item -> Routes.open(requireContext(), item.data));
        binding.rvJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvJobs.setAdapter(jobs);
        binding.rvActivity.setAdapter(activity);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.btnRetry.setOnClickListener(v -> viewModel.refresh());
        binding.btnFinishProfile.setOnClickListener(v -> startActivity(new Intent(requireContext(), OnboardingActivity.class)));
        binding.cardShift.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (tag instanceof String) {
                startActivity(new Intent(requireContext(), ShiftDetailActivity.class).putExtra("id", (String) tag));
            }
        });
        viewModel.dashboard().observe(getViewLifecycleOwner(), this::render);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void render(Resource<Dtos.Dashboard> resource) {
        boolean loading = resource.state == Resource.State.LOADING;
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.refresh.setRefreshing(false);
        if (resource.state == Resource.State.ERROR) {
            binding.tvError.setText(resource.message);
            binding.tvError.setVisibility(View.VISIBLE);
            binding.btnRetry.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvError.setVisibility(resource.offline ? View.VISIBLE : View.GONE);
        binding.btnRetry.setVisibility(View.GONE);
        if (resource.offline) binding.tvError.setText(resource.message);
        if (resource.data == null) return;
        Dtos.Dashboard data = resource.data;
        binding.tvGreeting.setText(Format.greeting() + ",");
        binding.tvName.setText(data.greetingName);
        binding.tvRole.setText(data.healthcareRole == null || data.healthcareRole.isEmpty() ? "Healthcare professional" : data.healthcareRole);
        binding.tvCredential.setText("Credential health — " + data.completion + "% complete");
        binding.progressProfile.setProgress(data.completion);
        String note = Format.status(data.verificationStatus);
        if (data.missing != null && !data.missing.isEmpty()) {
            StringBuilder missing = new StringBuilder();
            for (String item : data.missing) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(item);
            }
            note = note + ". Still needed: " + missing;
        }
        if (data.expiringDocuments != null && !data.expiringDocuments.isEmpty()) {
            note = note + ". A document is nearing expiry.";
        }
        binding.tvCredentialNote.setText(note);
        binding.btnFinishProfile.setVisibility(data.canApply ? View.GONE : View.VISIBLE);
        Dtos.Counts counts = data.counts == null ? new Dtos.Counts() : data.counts;
        binding.tvStatApplied.setText(String.valueOf(counts.total));
        binding.tvStatReview.setText(String.valueOf(counts.underReview));
        binding.tvStatOffers.setText(String.valueOf(counts.offered));
        binding.tvStatConfirmed.setText(String.valueOf(counts.confirmed));
        if (data.upcomingShift != null && data.upcomingShift.job != null) {
            binding.cardShift.setVisibility(View.VISIBLE);
            binding.cardShift.setTag(data.upcomingShift.id);
            binding.tvShiftTitle.setText(data.upcomingShift.job.hospitalName);
            binding.tvShiftMeta.setText(data.upcomingShift.job.title + "\n" + Format.shift(data.upcomingShift.job.shiftDate, data.upcomingShift.job.startTime, data.upcomingShift.job.endTime));
        } else {
            binding.cardShift.setVisibility(View.GONE);
        }
        jobs.submitList(data.recommendedJobs);
        activity.submitList(data.recentActivity);
        boolean empty = (data.recommendedJobs == null || data.recommendedJobs.isEmpty()) && (data.recentActivity == null || data.recentActivity.isEmpty());
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setText(empty ? "No open jobs yet. Pull to refresh when hospitals publish shifts." : "");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
