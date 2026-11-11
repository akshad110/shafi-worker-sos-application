package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.core.Format;
import com.example.shafi.core.PayRules;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.ItemShiftBinding;

public class ShiftAdapter extends ListAdapter<Dtos.ShiftItem, ShiftAdapter.Holder> {
    public interface OnOpen { void open(Dtos.ShiftItem item); }

    private final OnOpen listener;

    public ShiftAdapter(OnOpen listener) {
        super(new DiffUtil.ItemCallback<Dtos.ShiftItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull Dtos.ShiftItem a, @NonNull Dtos.ShiftItem b) {
                return a.id != null && a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Dtos.ShiftItem a, @NonNull Dtos.ShiftItem b) {
                return a.status != null && a.status.equals(b.status);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemShiftBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dtos.ShiftItem item = getItem(position);
        Dtos.JobItem job = item.job;
        String role = job != null && job.healthcareRole != null && !job.healthcareRole.isEmpty() && !"Shift".equals(job.healthcareRole)
                ? job.healthcareRole
                : (job != null && job.title != null && !job.title.isEmpty() && !"Shift".equals(job.title)
                ? job.title
                : (item.department != null && !item.department.isEmpty() ? item.department : "Shift"));
        holder.binding.tvTitle.setText(role);
        String hospital = item.hospitalName != null && !item.hospitalName.isEmpty()
                ? item.hospitalName
                : (job == null || job.hospitalName == null ? "" : job.hospitalName);
        String department = item.department != null && !item.department.isEmpty()
                ? item.department
                : (job == null || job.department == null ? "" : job.department);
        holder.binding.tvHospital.setText(hospital + (department.isEmpty() ? "" : " · " + department));
        String start = item.startTime != null && !item.startTime.isEmpty() ? item.startTime : (job == null || job.startTime == null ? "" : job.startTime);
        String end = item.endTime != null && !item.endTime.isEmpty() ? item.endTime : (job == null || job.endTime == null ? "" : job.endTime);
        holder.binding.tvWhen.setText(Format.prettyDate(Format.shiftDay(item))
                + (start.isEmpty() ? "" : " · " + start + (end.isEmpty() ? "" : "–" + end)));
        holder.binding.tvStatus.setText(Format.status(item.status));
        holder.binding.tvPay.setText(PayRules.of(item).card);
        holder.itemView.setOnClickListener(v -> listener.open(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemShiftBinding binding;
        Holder(ItemShiftBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
