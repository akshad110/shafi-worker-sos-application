package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.ItemApplicationBinding;

public class ApplicationAdapter extends ListAdapter<Dtos.ApplicationItem, ApplicationAdapter.Holder> {
    public interface OnOpen { void open(Dtos.ApplicationItem item); }

    private final OnOpen listener;

    public ApplicationAdapter(OnOpen listener) {
        super(new DiffUtil.ItemCallback<Dtos.ApplicationItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull Dtos.ApplicationItem a, @NonNull Dtos.ApplicationItem b) {
                return a.id != null && a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Dtos.ApplicationItem a, @NonNull Dtos.ApplicationItem b) {
                return a.status != null && a.status.equals(b.status);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemApplicationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dtos.ApplicationItem item = getItem(position);
        Dtos.JobItem job = item.job;
        holder.binding.tvTitle.setText(job == null ? "Application" : job.title);
        holder.binding.tvHospital.setText(job == null ? "" : job.hospitalName);
        holder.binding.tvWhen.setText(job == null ? "" : Format.shift(job.shiftDate, job.startTime, job.endTime));
        holder.binding.tvPay.setText(job == null ? "" : Format.money(job.compensationMinor, job.currency));
        holder.binding.tvStatus.setText(Format.status(item.status));
        holder.itemView.setOnClickListener(v -> listener.open(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemApplicationBinding binding;
        Holder(ItemApplicationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
