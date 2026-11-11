package com.example.shafi.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.R;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.ui.screens.ApplyActivity;
import com.example.shafi.databinding.ItemJobBinding;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.Map;

public class JobAdapter extends ListAdapter<Dtos.JobItem, JobAdapter.Holder> {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Dtos.JobItem> details = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();
    private String expandedId;
    private String loadingId;

    public JobAdapter() {
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
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemJobBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Dtos.JobItem job = getItem(position);
        ItemJobBinding row = holder.binding;
        row.tvHospital.setText(job.hospitalName);
        row.tvVerified.setVisibility(job.hospitalVerified ? View.VISIBLE : View.GONE);
        row.tvUrgent.setVisibility(job.urgent ? View.VISIBLE : View.GONE);
        row.tvTitle.setText(job.title);
        row.tvMeta.setText(job.healthcareRole + " · " + job.department);
        row.tvWhen.setText(Format.shift(job.shiftDate, job.startTime, job.endTime));
        String place = job.city == null ? "" : job.city;
        if (job.distanceKm != null) place = place + " · " + job.distanceKm + " km";
        row.tvPlace.setText(place);
        row.tvPay.setText(Format.money(job.compensationMinor, job.currency) + " " + Format.payUnit(job.paymentUnit));

        boolean open = job.id != null && job.id.equals(expandedId);
        row.detailPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        if (row.getRoot() instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) row.getRoot();
            card.setStrokeColor(ContextCompat.getColor(card.getContext(), open ? R.color.emerald : R.color.line));
            float density = card.getResources().getDisplayMetrics().density;
            card.setRadius(open ? 0f : 16f * density);
        }
        row.getRoot().setOnClickListener(v -> toggle(v, job.id));
        if (!open) return;
        row.getRoot().post(() -> {
            ViewParent parent = row.getRoot().getParent();
            if (parent instanceof View) ((View) parent).requestLayout();
        });

        Dtos.JobItem full = details.containsKey(job.id) ? details.get(job.id) : job;
        String failure = errors.get(job.id);
        if (failure != null && !details.containsKey(job.id)) {
            row.tvDetailError.setVisibility(View.VISIBLE);
            row.tvDetailError.setText(failure);
            row.tvDetail.setText("Details could not be loaded.");
            row.btnApply.setVisibility(View.GONE);
            return;
        }
        bindDetails(row, full);
        row.btnApply.setOnClickListener(v -> confirmApply(v, job.id));
    }

    private void bindDetails(ItemJobBinding row, Dtos.JobItem item) {
        String body = Format.block("Role", item.healthcareRole + " · " + item.department)
                + Format.block("When", Format.shift(item.shiftDate, item.startTime, item.endTime))
                + Format.block("Where", (item.address == null ? "" : item.address + ", ") + item.city)
                + Format.block("Pay", Format.money(item.compensationMinor, item.currency) + " " + Format.payUnit(item.paymentUnit))
                + Format.block("Vacancies", item.filledCount + " filled of " + item.vacancies)
                + Format.block("Deadline", item.deadline)
                + Format.block("Description", item.description)
                + Format.block("Reporting", item.reportingInstructions)
                + Format.bullets("Qualifications", item.qualifications)
                + Format.bullets("Skills", item.skills)
                + Format.bullets("Certifications", item.certifications)
                + Format.block("Experience", item.experienceYears == null || item.experienceYears == 0 ? "" : item.experienceYears + " years");
        String text = body.trim();
        row.tvDetail.setText(text);
        row.tvDetailError.setVisibility(View.GONE);
        boolean ready = item.id != null && details.containsKey(item.id);
        row.detailProgress.setVisibility(!ready && text.isEmpty() ? View.VISIBLE : View.GONE);
        row.btnApply.setVisibility(View.VISIBLE);
        if (item.hasApplied) {
            row.btnApply.setText("Already applied");
            row.btnApply.setEnabled(false);
        } else if (ready && !item.eligible) {
            row.btnApply.setText("Apply");
            row.btnApply.setEnabled(false);
            row.tvDetailError.setVisibility(View.VISIBLE);
            row.tvDetailError.setText(item.eligibilityReason == null ? "You cannot apply for this job yet." : item.eligibilityReason);
        } else {
            row.btnApply.setText("Apply now");
            row.btnApply.setEnabled(true);
        }
    }

    private void toggle(View view, String id) {
        if (id == null) return;
        String previous = expandedId;
        expandedId = id.equals(previous) ? null : id;
        refresh(previous);
        refresh(expandedId);
        if (expandedId != null && !details.containsKey(expandedId) && !expandedId.equals(loadingId)) {
            load(view.getContext().getApplicationContext(), expandedId);
        }
    }

    private void load(android.content.Context context, String id) {
        loadingId = id;
        refresh(id);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.JobItem full = new WorkerRepository(context).job(id);
                main.post(() -> {
                    if (loadingId != null && loadingId.equals(id)) loadingId = null;
                    if (full != null) {
                        details.put(id, full);
                        errors.remove(id);
                    }
                    refresh(id);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (loadingId != null && loadingId.equals(id)) loadingId = null;
                    errors.put(id, error.getMessage() == null ? "Couldn't load this job." : error.getMessage());
                    refresh(id);
                });
            }
        });
    }

    private void confirmApply(View view, String id) {
        Dtos.JobItem job = details.containsKey(id) ? details.get(id) : null;
        if (job != null && (job.hasApplied || !job.eligible)) return;
        Intent intent = new Intent(view.getContext(), ApplyActivity.class);
        intent.putExtra("id", id);
        if (job != null && job.title != null) {
            String place = job.hospitalName == null ? "" : job.hospitalName;
            intent.putExtra("title", place.isEmpty() ? job.title : job.title + " at " + place);
        }
        view.getContext().startActivity(intent);
    }

    private void refresh(String id) {
        if (id == null) return;
        for (int i = 0; i < getItemCount(); i++) {
            Dtos.JobItem item = getItem(i);
            if (item != null && id.equals(item.id)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemJobBinding binding;

        Holder(ItemJobBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.detailPanel.setClickable(true);
            binding.detailPanel.setFocusable(true);
        }
    }
}
