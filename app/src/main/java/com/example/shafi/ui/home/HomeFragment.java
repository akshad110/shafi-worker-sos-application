package com.example.shafi.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.R;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.FragmentHomeBinding;
import com.example.shafi.ui.ActivityAdapter;
import com.example.shafi.ui.JobAdapter;
import com.example.shafi.ui.Routes;
import com.example.shafi.ui.screens.OnboardingActivity;
import com.example.shafi.ui.screens.ShiftDetailActivity;
import com.example.shafi.viewmodel.HomeDashViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeDashViewModel viewModel;
    private JobAdapter jobs;
    private ActivityAdapter activity;
    private final List<Dtos.JobItem> recommended = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String locationChip = "";
    private Runnable pendingSearch;
    private int searchToken;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(HomeDashViewModel.class);
        jobs = new JobAdapter();
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
        binding.etLocation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s != null && s.toString().trim().length() > 0 && !locationChip.isEmpty()) {
                    locationChip = "";
                    fillChips();
                }
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> applyLocation();
                handler.postDelayed(pendingSearch, 250);
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
        boolean loading = resource.state == Resource.State.LOADING && resource.data == null;
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
        binding.tvClock.setText(Format.clock());
        binding.tvName.setText(data.greetingName);
        binding.tvRole.setText(data.healthcareRole == null || data.healthcareRole.isEmpty() ? "Healthcare professional" : data.healthcareRole);
        binding.tvCredential.setText(data.completion + "% complete");
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
        binding.tvStatOffers.setText(String.valueOf(counts.rejected));
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
        recommended.clear();
        if (data.recommendedJobs != null) recommended.addAll(data.recommendedJobs);
        fillChips();
        applyLocation();
        activity.submitList(data.recentActivity);
    }

    private void fillChips() {
        if (binding == null) return;
        Set<String> places = new LinkedHashSet<>();
        for (Dtos.JobItem job : recommended) {
            if (job.city != null && !job.city.trim().isEmpty()) places.add(job.city.trim());
            if (job.state != null && !job.state.trim().isEmpty()) places.add(job.state.trim());
            if (places.size() >= 6) break;
        }
        binding.locationChips.removeAllViews();
        binding.locationChips.addView(chip("All", locationChip.isEmpty()));
        for (String place : places) binding.locationChips.addView(chip(place, place.equalsIgnoreCase(locationChip)));
    }

    private TextView chip(String label, boolean selected) {
        TextView view = new TextView(requireContext());
        view.setText(label);
        view.setTextSize(13);
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));
        view.setLayoutParams(params);
        view.setBackgroundResource(selected ? R.drawable.bg_chip_filled : R.drawable.bg_chip);
        view.setTextColor(requireContext().getColor(selected ? R.color.white : R.color.navy));
        view.setOnClickListener(v -> {
            locationChip = "All".equals(label) ? "" : label;
            fillChips();
            applyLocation();
        });
        return view;
    }

    private void applyLocation() {
        if (binding == null) return;
        String typed = binding.etLocation.getText().toString().trim();
        String chip = locationChip;
        int token = ++searchToken;
        if (typed.isEmpty() && chip.isEmpty()) {
            jobs.submitList(new ArrayList<>(recommended));
            binding.tvEmpty.setVisibility(recommended.isEmpty() ? View.VISIBLE : View.GONE);
            if (recommended.isEmpty()) binding.tvEmpty.setText("No open jobs yet. Pull to refresh when hospitals publish shifts.");
            return;
        }
        String remote = typed.isEmpty() ? chip : typed;
        android.content.Context app = requireContext().getApplicationContext();
        AppExecutors.IO.execute(() -> {
            try {
                WorkerRepository repository = new WorkerRepository(app);
                Dtos.JobPage page = repository.jobs(remote, "", false, "newest", 1);
                List<Dtos.JobItem> found = page == null || page.items == null ? new ArrayList<>() : page.items;
                List<Dtos.JobItem> matched = new ArrayList<>();
                for (Dtos.JobItem job : found) {
                    if (matches(job, typed, chip)) matched.add(job);
                }
                handler.post(() -> publishJobs(token, matched));
            } catch (Exception error) {
                List<Dtos.JobItem> local = new ArrayList<>();
                for (Dtos.JobItem job : recommended) {
                    if (matches(job, typed, chip)) local.add(job);
                }
                handler.post(() -> publishJobs(token, local));
            }
        });
    }

    private void publishJobs(int token, List<Dtos.JobItem> matched) {
        if (binding == null || token != searchToken) return;
        jobs.submitList(matched);
        binding.tvEmpty.setVisibility(matched.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setText(matched.isEmpty() ? "No hospital matches this city, state, or name." : "");
    }

    private boolean matches(Dtos.JobItem job, String typed, String chip) {
        if (!chip.isEmpty() && !containsPlace(job, chip)) return false;
        if (typed.isEmpty()) return true;
        return containsPlace(job, typed);
    }

    private boolean containsPlace(Dtos.JobItem job, String query) {
        String needle = query.toLowerCase(Locale.US);
        return text(job.hospitalName).contains(needle)
                || text(job.city).contains(needle)
                || text(job.state).contains(needle)
                || text(job.address).contains(needle);
    }

    private String text(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        binding = null;
    }
}
