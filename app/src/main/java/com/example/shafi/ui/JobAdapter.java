package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.ItemJobBinding;

public class JobAdapter extends ListAdapter<Dtos.JobItem, JobAdapter.Holder> {
    public interface OnJob { void open(Dtos.JobItem job); }

    private final OnJob listener;

    public JobAdapter(OnJob listener) {
        super(new DiffUtil.ItemCallback<Dtos.JobItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull Dtos.JobItem a, @NonNull Dtos.JobItem b) {
                return a.id != null && a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Dtos.JobItem a, @NonNull Dtos.JobItem b) {
                return a.urgent == b.urgent && a.compensationMinor == b.compensationMinor && a.filledCount == b.filledCount;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemJobBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dtos.JobItem job = getItem(position);
        holder.binding.tvHospital.setText(job.hospitalName);
        holder.binding.tvVerified.setVisibility(job.hospitalVerified ? View.VISIBLE : View.GONE);
        holder.binding.tvUrgent.setVisibility(job.urgent ? View.VISIBLE : View.GONE);
        holder.binding.tvTitle.setText(job.title);
        holder.binding.tvMeta.setText(job.healthcareRole + " · " + job.department);
        holder.binding.tvWhen.setText(Format.shift(job.shiftDate, job.startTime, job.endTime));
        String place = job.city == null ? "" : job.city;
        if (job.distanceKm != null) place = place + " · " + job.distanceKm + " km";
        holder.binding.tvPlace.setText(place);
        holder.binding.tvPay.setText(Format.money(job.compensationMinor, job.currency) + " " + Format.payUnit(job.paymentUnit));
        holder.itemView.setOnClickListener(v -> listener.open(job));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemJobBinding binding;
        Holder(ItemJobBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
