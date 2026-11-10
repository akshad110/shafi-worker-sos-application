package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.core.Format;
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
        holder.binding.tvTitle.setText(job == null ? "Shift" : job.title);
        holder.binding.tvHospital.setText(job == null ? "" : job.hospitalName);
        holder.binding.tvWhen.setText(job == null ? "" : Format.shift(job.shiftDate, job.startTime, job.endTime));
        holder.binding.tvStatus.setText(Format.status(item.status));
        String pay = "Payment not recorded";
        if (item.payment != null) {
            pay = Format.status(item.payment.status) + " · " + Format.money(item.payment.amountMinor, item.payment.currency);
        }
        holder.binding.tvPay.setText(pay);
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
