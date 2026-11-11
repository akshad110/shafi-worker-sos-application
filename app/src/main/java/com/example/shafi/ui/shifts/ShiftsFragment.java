package com.example.shafi.ui.shifts;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.shafi.R;
import com.example.shafi.core.Format;
import com.example.shafi.core.Resource;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.FragmentShiftsBinding;
import com.example.shafi.ui.DayAdapter;
import com.example.shafi.ui.ShiftAdapter;
import com.example.shafi.ui.screens.AttendanceActivity;
import com.example.shafi.ui.screens.ShiftDetailActivity;
import com.example.shafi.viewmodel.ShiftsViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShiftsFragment extends Fragment {
    private FragmentShiftsBinding binding;
    private ShiftsViewModel viewModel;
    private DayAdapter days;
    private String selectedDay = Format.todayKey();
    private boolean dayChosen;
    private String statusFilter = "";
    private String department = "";
    private List<Dtos.ShiftItem> all = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentShiftsBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ShiftsViewModel.class);
        ShiftAdapter adapter = new ShiftAdapter(item ->
                startActivity(new Intent(requireContext(), ShiftDetailActivity.class).putExtra("id", item.id)));
        days = new DayAdapter(key -> {
            selectedDay = key;
            dayChosen = true;
            statusFilter = "";
            show(adapter);
        });
        binding.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvItems.setAdapter(adapter);
        binding.rvDays.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvDays.setAdapter(days);
        binding.refresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.btnAttendance.setOnClickListener(v -> startActivity(new Intent(requireContext(), AttendanceActivity.class)));
        binding.btnDayPrev.setOnClickListener(v -> binding.rvDays.smoothScrollBy(-dp(220), 0));
        binding.btnDayNext.setOnClickListener(v -> binding.rvDays.smoothScrollBy(dp(220), 0));
        binding.chipUpcoming.setOnClickListener(v -> chooseStatus("UPCOMING", adapter));
        binding.chipActive.setOnClickListener(v -> chooseStatus("ACTIVE", adapter));
        binding.chipCompleted.setOnClickListener(v -> chooseStatus("COMPLETED", adapter));
        binding.chipCancelled.setOnClickListener(v -> chooseStatus("CANCELLED", adapter));
        binding.btnDepartment.setOnClickListener(v -> openDepartments(adapter));
        viewModel.shifts().observe(getViewLifecycleOwner(), resource -> {
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

    private void chooseStatus(String status, ShiftAdapter adapter) {
        statusFilter = status.equals(statusFilter) ? "" : status;
        show(adapter);
    }

    private void openDepartments(ShiftAdapter adapter) {
        PopupMenu menu = new PopupMenu(requireContext(), binding.btnDepartment);
        menu.getMenu().add("All departments");
        for (String name : departments()) menu.getMenu().add(name);
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            department = "All departments".equals(title) ? "" : title;
            binding.btnDepartment.setText(title);
            show(adapter);
            return true;
        });
        menu.show();
    }

    private void show(ShiftAdapter adapter) {
        if (binding == null) return;
        List<Dtos.ShiftItem> scoped = new ArrayList<>();
        for (Dtos.ShiftItem item : all) {
            if (matchesDepartment(item)) scoped.add(item);
        }
        int coming = count(scoped, "UPCOMING");
        int now = count(scoped, "ACTIVE");
        int done = count(scoped, "COMPLETED");
        int cancelled = count(scoped, "CANCELLED");
        binding.chipUpcoming.setText("Coming " + coming);
        binding.chipActive.setText("Now " + now);
        binding.chipCompleted.setText("Done " + done);
        binding.chipCancelled.setText("Cancelled " + cancelled);
        paintCount(binding.chipUpcoming, "UPCOMING".equals(statusFilter));
        paintCount(binding.chipActive, "ACTIVE".equals(statusFilter));
        paintCount(binding.chipCompleted, "COMPLETED".equals(statusFilter));
        paintCount(binding.chipCancelled, "CANCELLED".equals(statusFilter));

        Set<String> marked = new LinkedHashSet<>();
        for (Dtos.ShiftItem item : scoped) {
            String day = Format.shiftDay(item);
            if (!day.isEmpty()) marked.add(day);
        }
        if (!dayChosen && !marked.isEmpty()) {
            String today = Format.todayKey();
            selectedDay = marked.contains(today) ? today : marked.iterator().next();
            dayChosen = true;
        }
        List<DayAdapter.Day> strip = new ArrayList<>();
        String start = Format.plusDays(Format.todayKey(), -2);
        for (int i = 0; i < 16; i++) {
            String key = Format.plusDays(start, i);
            DayAdapter.Day day = new DayAdapter.Day();
            day.key = key;
            day.weekday = Format.weekday(key);
            day.number = Format.dayNumber(key);
            day.hasShift = marked.contains(key);
            day.selected = statusFilter.isEmpty() && key.equals(selectedDay);
            strip.add(day);
        }
        days.submit(strip);

        List<Dtos.ShiftItem> visible = new ArrayList<>();
        for (Dtos.ShiftItem item : scoped) {
            if (!statusFilter.isEmpty()) {
                if (statusFilter.equals(item.status)) visible.add(item);
            } else if (selectedDay.equals(Format.shiftDay(item))) {
                visible.add(item);
            }
        }
        adapter.submitList(visible);
        boolean showEmpty = visible.isEmpty() && binding.progress.getVisibility() != View.VISIBLE;
        binding.tvState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            binding.tvState.setText(statusFilter.isEmpty()
                    ? "No shift on this day."
                    : "No " + statusFilter.toLowerCase() + " shifts.");
        }
    }

    private void paintCount(TextView chip, boolean on) {
        chip.setBackgroundResource(on ? R.drawable.bg_count_on : android.R.color.transparent);
        chip.setTextColor(requireContext().getColor(on ? R.color.white : R.color.text_secondary));
    }

    private int count(List<Dtos.ShiftItem> items, String status) {
        int total = 0;
        for (Dtos.ShiftItem item : items) {
            if (status.equals(item.status)) total++;
        }
        return total;
    }

    private boolean matchesDepartment(Dtos.ShiftItem item) {
        if (department.isEmpty()) return true;
        String value = item.department;
        if ((value == null || value.isEmpty()) && item.job != null) value = item.job.department;
        return department.equals(value);
    }

    private Set<String> departments() {
        Set<String> names = new LinkedHashSet<>();
        for (Dtos.ShiftItem item : all) {
            String value = item.department;
            if ((value == null || value.isEmpty()) && item.job != null) value = item.job.department;
            if (value != null && !value.isEmpty()) names.add(value);
        }
        return names;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
