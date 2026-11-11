package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.R;
import com.example.shafi.databinding.ItemDayBinding;

import java.util.ArrayList;
import java.util.List;

public class DayAdapter extends RecyclerView.Adapter<DayAdapter.Holder> {
    public static class Day {
        public String key;
        public String weekday;
        public int number;
        public boolean hasShift;
        public boolean selected;
    }

    public interface OnDay { void select(String key); }

    private final List<Day> days = new ArrayList<>();
    private final OnDay listener;

    public DayAdapter(OnDay listener) {
        this.listener = listener;
    }

    public void submit(List<Day> next) {
        days.clear();
        if (next != null) days.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Day day = days.get(position);
        holder.binding.tvWeekday.setText(day.weekday);
        holder.binding.tvDayNumber.setText(String.valueOf(day.number));
        holder.binding.dayDot.setVisibility(day.hasShift ? View.VISIBLE : View.INVISIBLE);
        holder.binding.dayBox.setBackgroundResource(day.selected ? R.drawable.bg_day_selected : R.drawable.bg_day);
        holder.binding.dayBox.setOnClickListener(v -> listener.select(day.key));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDayBinding binding;
        Holder(ItemDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
