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
    private String reviewKey = "";
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
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh(true));
        binding.rvJobs.setItemAnimator(null);
        binding.rvActivity.setItemAnimator(null);
        binding.rvJobs.setItemViewCacheSize(12);
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
        fillReviews(data);
        binding.btnFinishProfile.setVisibility(data.canApply ? View.GONE : View.VISIBLE);
        Dtos.Counts counts = data.counts == null ? new Dtos.Counts() : data.counts;
        binding.tvStatApplied.setText(String.valueOf(counts.total));
        binding.tvStatReview.setText(String.valueOf(counts.underReview));
        binding.tvStatOffers.setText(String.valueOf(counts.rejected));
        binding.tvStatConfirmed.setText(String.valueOf(counts.confirmed));
        if (data.upcomingShift != null && data.upcomingShift.job != null) {
            Dtos.JobItem shiftJob = data.upcomingShift.job;
            String hospital = shiftJob.hospitalName == null || shiftJob.hospitalName.isEmpty() ? "your hospital" : shiftJob.hospitalName;
            binding.cardShift.setTag(data.upcomingShift.id);
            binding.tvShiftTitle.setText("Active shift: " + hospital);
            String when = Format.shift(Format.prettyDate(shiftJob.shiftDate), shiftJob.startTime, shiftJob.endTime);
            binding.tvShiftMeta.setText(when + ". Check details.");
        } else {
            binding.cardShift.setTag(null);
            String status = Format.status(data.verificationStatus);
            binding.tvShiftTitle.setText(status.isEmpty() ? "No active shift" : status);
            binding.tvShiftMeta.setText(data.canApply ? "You can apply for open shifts." : "Finish your profile before applying.");
        }
        recommended.clear();
        if (data.recommendedJobs != null) recommended.addAll(data.recommendedJobs);
        boolean filtering = !locationChip.isEmpty() || binding.etLocation.getText().toString().trim().length() > 0;
        if (!filtering) jobs.submitList(data.recommendedJobs);
        fillChips();
        if (filtering) applyLocation();
        activity.submitList(data.recentActivity);
    }

    private void fillReviews(Dtos.Dashboard data) {
        if (binding == null) return;
        StringBuilder key = new StringBuilder();
        if (data.missing != null) for (String item : data.missing) key.append(item).append('|');
        if (data.expiringDocuments != null) {
            for (Dtos.DocItem doc : data.expiringDocuments) {
                if (doc == null) continue;
                key.append(doc.id).append(doc.status).append(doc.expiresAt);
            }
        }
        String next = key.toString();
        if (next.equals(reviewKey) && binding.reviewList.getChildCount() > 0) return;
        reviewKey = next;
        binding.reviewList.removeAllViews();
        int shown = 0;
        if (data.missing != null) {
            for (String item : data.missing) {
                if (item == null || item.trim().isEmpty() || shown >= 4) continue;
                if (shown > 0) binding.reviewList.addView(reviewDivider());
                binding.reviewList.addView(reviewRow(prettyLabel(item), "Still needed", false));
                shown++;
            }
        }
        if (data.expiringDocuments != null) {
            for (Dtos.DocItem doc : data.expiringDocuments) {
                if (doc == null || shown >= 4) continue;
                if (shown > 0) binding.reviewList.addView(reviewDivider());
                String name = prettyLabel(doc.fileName != null && !doc.fileName.isEmpty() ? doc.fileName : doc.type);
                String due = doc.expiresAt == null || doc.expiresAt.isEmpty() ? Format.status(doc.status) : "Due " + Format.prettyDate(doc.expiresAt);
                boolean complete = "APPROVED".equals(doc.status) || "VERIFIED".equals(doc.status);
                binding.reviewList.addView(reviewRow(name, due, complete));
                shown++;
            }
        }
        if (shown == 0) {
            binding.reviewList.addView(reviewRow("Credentials", "Nothing is waiting for review", true));
        }
    }

    private View reviewDivider() {
        View line = new View(requireContext());
        line.setBackgroundColor(0xFFE3EAF1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(4);
        params.bottomMargin = dp(4);
        line.setLayoutParams(params);
        return line;
    }

    private View reviewRow(String title, String detail, boolean complete) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView icon = new TextView(requireContext());
        icon.setText(complete ? "✓" : "•");
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setTextColor(requireContext().getColor(complete ? R.color.success : R.color.pending));
        icon.setTextSize(14);
        icon.setBackgroundResource(complete ? R.drawable.bg_stat_green : R.drawable.bg_pill_pending);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        row.addView(icon, iconParams);

        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(12));
        textParams.setMarginEnd(dp(10));

        TextView heading = new TextView(requireContext());
        heading.setText(title);
        heading.setTextColor(requireContext().getColor(R.color.navy));
        heading.setTextSize(15);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        text.addView(heading);

        TextView sub = new TextView(requireContext());
        sub.setText(detail == null ? "" : detail);
        sub.setTextColor(requireContext().getColor(R.color.text_secondary));
        sub.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        text.addView(sub, subParams);
        row.addView(text, textParams);

        TextView pill = new TextView(requireContext());
        pill.setText(complete ? "Complete" : "In progress");
        pill.setTextSize(12);
        pill.setTypeface(pill.getTypeface(), android.graphics.Typeface.BOLD);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        pill.setBackgroundResource(complete ? R.drawable.bg_stat_green : R.drawable.bg_pill_pending);
        pill.setTextColor(requireContext().getColor(complete ? R.color.success : R.color.pending));
        row.addView(pill);
        return row;
    }

    private String prettyLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Document";
        String cleaned = raw.trim().replace('_', ' ');
        if (cleaned.length() == 1) return cleaned.toUpperCase(Locale.US);
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
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
