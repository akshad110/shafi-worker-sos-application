package com.example.shafi.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.data.Dtos;
import com.example.shafi.databinding.ItemActivityBinding;

public class ActivityAdapter extends ListAdapter<Dtos.ActivityItem, ActivityAdapter.Holder> {
    public interface OnOpen { void open(Dtos.ActivityItem item); }

    private final OnOpen listener;

    public ActivityAdapter(OnOpen listener) {
        super(new DiffUtil.ItemCallback<Dtos.ActivityItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull Dtos.ActivityItem a, @NonNull Dtos.ActivityItem b) {
                return a.id != null && a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Dtos.ActivityItem a, @NonNull Dtos.ActivityItem b) {
                return a.read == b.read && a.title != null && a.title.equals(b.title);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemActivityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dtos.ActivityItem item = getItem(position);
        holder.binding.tvTitle.setText(item.title);
        holder.binding.tvBody.setText(item.body);
        holder.binding.tvUnread.setVisibility(item.read ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.itemView.setOnClickListener(v -> listener.open(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemActivityBinding binding;
        Holder(ItemActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
