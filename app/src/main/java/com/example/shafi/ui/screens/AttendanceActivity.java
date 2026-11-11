package com.example.shafi.ui.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.R;
import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityAttendanceBinding;
import com.example.shafi.databinding.ItemAttendanceBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AttendanceActivity extends AppCompatActivity {
    private ActivityAttendanceBinding binding;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.rvDays.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDays.setAdapter(adapter);
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        WorkerRepository repository = new WorkerRepository(this);
        AppExecutors.IO.execute(() -> {
            try {
                List<Dtos.ShiftItem> shifts = repository.shifts();
                runOnUiThread(() -> show(shifts, null));
            } catch (Exception error) {
                List<Dtos.ShiftItem> cached = repository.cachedShifts();
                runOnUiThread(() -> show(cached, cached.isEmpty() ? error.getMessage() : null));
            }
        });
    }

    private void show(List<Dtos.ShiftItem> shifts, String error) {
        binding.progress.setVisibility(View.GONE);
        List<Dtos.ShiftItem> rows = new ArrayList<>();
        if (shifts != null) {
            for (Dtos.ShiftItem item : shifts) {
                if (!Format.shiftDay(item).isEmpty()) rows.add(item);
            }
        }
        Collections.sort(rows, Comparator.comparing(Format::shiftDay));
        adapter.submit(rows);
        boolean empty = rows.isEmpty();
        binding.tvState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            binding.tvState.setTextColor(getColor(error == null ? R.color.text_secondary : R.color.error));
            binding.tvState.setText(error == null || error.contains("went wrong")
                    ? (error == null
                    ? "Attendance appears here after a hospital selects you for a shift."
                    : "Attendance could not be loaded. Check the connection and open this screen again.")
                    : error);
        }
        binding.tvSummary.setText(empty
                ? "Marks recorded by the hospital for your selected shifts."
                : rows.size() + " shift days from your selected work.");
    }

    private static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<Dtos.ShiftItem> rows = new ArrayList<>();

        void submit(List<Dtos.ShiftItem> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemAttendanceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Dtos.ShiftItem item = rows.get(position);
            String day = Format.shiftDay(item);
            Dtos.JobItem job = item.job;
            holder.binding.tvWeekday.setText(Format.weekday(day));
            holder.binding.tvDayNumber.setText(day.isEmpty() ? "" : String.valueOf(Format.dayNumber(day)));
            String title = item.hospitalName != null && !item.hospitalName.isEmpty()
                    ? item.hospitalName
                    : (job == null || job.hospitalName == null ? "Shift" : job.hospitalName);
            String role = job == null ? "" : (job.healthcareRole == null || job.healthcareRole.isEmpty() ? job.title : job.healthcareRole);
            holder.binding.tvDate.setText(Format.prettyDate(day) + (role == null || role.isEmpty() ? "" : " · " + role));
            String start = item.startTime != null && !item.startTime.isEmpty() ? item.startTime : (job == null ? "" : job.startTime);
            String end = item.endTime != null && !item.endTime.isEmpty() ? item.endTime : (job == null ? "" : job.endTime);
            holder.binding.tvTime.setText((start == null ? "" : start) + (end == null || end.isEmpty() ? "" : "–" + end) + (title.isEmpty() ? "" : " · " + title));
            String mark = item.attendanceMark == null ? "UNMARKED" : item.attendanceMark;
            paint(holder.binding.markPresent, "PRESENT".equals(mark));
            paint(holder.binding.markLate, "LATE".equals(mark));
            paint(holder.binding.markHalf, "HALF".equals(mark));
            paint(holder.binding.markAbsent, "ABSENT".equals(mark));
            holder.binding.tvMarkNote.setText(note(mark));
        }

        private void paint(android.widget.TextView chip, boolean on) {
            chip.setBackgroundResource(on ? R.drawable.bg_count_on : R.drawable.bg_chip);
            chip.setTextColor(chip.getContext().getColor(on ? R.color.white : R.color.text_secondary));
        }

        private String note(String mark) {
            if ("PRESENT".equals(mark)) return "Marked present";
            if ("LATE".equals(mark)) return "Marked late";
            if ("HALF".equals(mark)) return "Half day";
            if ("ABSENT".equals(mark)) return "Marked absent";
            return "Not marked · Not counted yet";
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ItemAttendanceBinding binding;
            Holder(ItemAttendanceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
