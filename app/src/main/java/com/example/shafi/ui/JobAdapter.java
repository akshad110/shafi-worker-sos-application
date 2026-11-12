package com.example.shafi.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.shafi.R;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ItemJobBinding;
import com.example.shafi.databinding.RowDetailBinding;
import com.example.shafi.databinding.SheetJobBinding;
import com.example.shafi.ui.screens.ApplyActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.Map;

public class JobAdapter extends ListAdapter<Dtos.JobItem, JobAdapter.Holder> {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Dtos.JobItem> details = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();
    private BottomSheetDialog sheet;
    private SheetJobBinding sheetBinding;
    private String sheetId;
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
        if (job.state != null && !job.state.isEmpty()) place = place.isEmpty() ? job.state : place + ", " + job.state;
        if (job.distanceKm != null) place = place + " · " + job.distanceKm + " km";
        row.tvPlace.setText(place);
        row.tvPay.setText(Format.money(job.compensationMinor, job.currency) + " " + Format.payUnit(job.paymentUnit));
        row.getRoot().setOnClickListener(v -> open(v, job.id));
    }

    private void open(View view, String id) {
        if (id == null) return;
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        sheetBinding = SheetJobBinding.inflate(LayoutInflater.from(view.getContext()));
        sheet = new BottomSheetDialog(view.getContext(), R.style.Theme_Shafi_BottomSheet);
        sheet.setContentView(sheetBinding.getRoot());
        sheet.setCancelable(true);
        sheet.setCanceledOnTouchOutside(true);
        sheetId = id;
        sheet.setOnShowListener(dialog -> {
            if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.6f);
            pinToBottom();
            capScroll();
        });
        Dtos.JobItem known = details.containsKey(id) ? details.get(id) : find(id);
        paint(known, details.containsKey(id));
        sheetBinding.btnClose.setOnClickListener(v -> sheet.dismiss());
        sheetBinding.btnApply.setOnClickListener(v -> {
            confirmApply(v, id);
            if (sheet != null) sheet.dismiss();
        });
        sheet.show();
        if (!details.containsKey(id) && !id.equals(loadingId)) {
            load(view.getContext().getApplicationContext(), id);
        }
    }

    private void paint(Dtos.JobItem item, boolean ready) {
        if (sheetBinding == null || item == null) return;
        sheetBinding.tvSheetTitle.setText(item.title == null ? "Shift" : item.title);
        sheetBinding.tvHospital.setText(item.hospitalName == null ? "" : item.hospitalName);
        sheetBinding.tvVerified.setVisibility(item.hospitalVerified ? View.VISIBLE : View.GONE);
        sheetBinding.tvUrgent.setVisibility(item.urgent ? View.VISIBLE : View.GONE);
        row(sheetBinding.rowRole, R.drawable.ic_document, "Role", join(item.healthcareRole, item.department));
        row(sheetBinding.rowWhen, R.drawable.ic_calendar, "When", Format.shift(Format.prettyDate(item.shiftDate), item.startTime, item.endTime));
        String where = (item.address == null || item.address.isEmpty() ? "" : item.address);
        if (item.city != null && !item.city.isEmpty()) {
            where = where.isEmpty() ? item.city : where + ", " + item.city;
        }
        if (item.state != null && !item.state.isEmpty()) {
            where = where.isEmpty() ? item.state : where + ", " + item.state;
        }
        row(sheetBinding.rowWhere, R.drawable.ic_home_black_24dp, "Where", where);
        row(sheetBinding.rowPay, R.drawable.ic_earnings, "Pay", Format.money(item.compensationMinor, item.currency) + " " + Format.payUnit(item.paymentUnit));
        sheetBinding.extraBox.removeAllViews();
        addSection("Vacancies", item.vacancies == 0 ? "" : item.filledCount + " filled of " + item.vacancies);
        addSection("Deadline", item.deadline);
        addSection("Description", item.description);
        addSection("Reporting", item.reportingInstructions);
        addSection("Qualifications", bullets(item.qualifications));
        addSection("Skills", bullets(item.skills));
        addSection("Certifications", bullets(item.certifications));
        addSection("Experience", item.experienceYears == null || item.experienceYears == 0 ? "" : item.experienceYears + " years");
        sheetBinding.extraBox.setVisibility(sheetBinding.extraBox.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        String failure = errors.get(item.id);
        boolean failed = failure != null && !ready;
        sheetBinding.progress.setVisibility(!ready && !failed ? View.VISIBLE : View.GONE);
        sheetBinding.tvError.setVisibility(View.GONE);
        if (failed) {
            sheetBinding.tvError.setVisibility(View.VISIBLE);
            sheetBinding.tvError.setText(failure);
            sheetBinding.btnApply.setEnabled(false);
            return;
        }
        if (item.hasApplied) {
            sheetBinding.btnApply.setText("Already applied");
            sheetBinding.btnApply.setEnabled(false);
        } else if (ready && !item.eligible) {
            sheetBinding.btnApply.setText("Apply");
            sheetBinding.btnApply.setEnabled(false);
            sheetBinding.tvError.setVisibility(View.VISIBLE);
            sheetBinding.tvError.setText(item.eligibilityReason == null ? "You cannot apply for this job yet." : item.eligibilityReason);
        } else {
            sheetBinding.btnApply.setText("Apply now");
            sheetBinding.btnApply.setEnabled(true);
        }
        capScroll();
    }

    private void addSection(String label, String value) {
        if (sheetBinding == null || value == null || value.trim().isEmpty()) return;
        View section = LayoutInflater.from(sheetBinding.getRoot().getContext())
                .inflate(R.layout.block_section, sheetBinding.extraBox, false);
        ((TextView) section.findViewById(R.id.tv_label)).setText(label);
        ((TextView) section.findViewById(R.id.tv_value)).setText(value.trim());
        sheetBinding.extraBox.addView(section);
    }

    private String bullets(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append("• ").append(value.trim());
        }
        return builder.toString();
    }

    private void row(RowDetailBinding row, int icon, String label, String value) {
        row.ivIcon.setImageResource(icon);
        row.tvLabel.setText(label);
        row.tvValue.setText(value == null || value.trim().isEmpty() ? "—" : value.trim());
    }

    private String join(String left, String right) {
        boolean hasLeft = left != null && !left.isEmpty();
        boolean hasRight = right != null && !right.isEmpty();
        if (hasLeft && hasRight) return left + " · " + right;
        if (hasLeft) return left;
        return hasRight ? right : "";
    }

    private void pinToBottom() {
        if (sheet == null) return;
        if (sheet.getWindow() != null) {
            WindowCompat.setDecorFitsSystemWindows(sheet.getWindow(), false);
            sheet.getWindow().setNavigationBarColor(0xFFFFFFFF);
        }
        FrameLayout panel = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (panel == null) return;
        View coordinator = (View) panel.getParent();
        View container = coordinator == null ? null : (View) coordinator.getParent();
        if (container != null) {
            container.setFitsSystemWindows(false);
            container.setPadding(0, 0, 0, 0);
        }
        if (coordinator != null && coordinator.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) coordinator.getLayoutParams();
            margins.bottomMargin = 0;
            coordinator.setLayoutParams(margins);
            coordinator.setPadding(0, 0, 0, 0);
        }
        panel.setPadding(0, 0, 0, 0);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(panel);
        behavior.setGestureInsetBottomIgnored(true);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(true);
        behavior.setDraggable(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
                view.setPadding(0, 0, 0, 0);
                if (coordinator.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) coordinator.getLayoutParams();
                    margins.bottomMargin = 0;
                    coordinator.setLayoutParams(margins);
                }
                panel.setPadding(0, 0, 0, 0);
                if (sheetBinding != null) {
                    int nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                    ViewGroup.MarginLayoutParams button = (ViewGroup.MarginLayoutParams) sheetBinding.btnApply.getLayoutParams();
                    int base = Math.round(16 * view.getResources().getDisplayMetrics().density);
                    if (button.bottomMargin != base + nav) {
                        button.bottomMargin = base + nav;
                        sheetBinding.btnApply.setLayoutParams(button);
                    }
                }
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(container);
        }
        panel.post(() -> {
            if (sheet == null || !sheet.isShowing()) return;
            View parent = (View) panel.getParent();
            if (parent != null && parent.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) parent.getLayoutParams();
                margins.bottomMargin = 0;
                parent.setLayoutParams(margins);
            }
            if (panel.getParent() instanceof View) {
                ((View) panel.getParent()).setPadding(0, 0, 0, 0);
            }
            panel.setPadding(0, 0, 0, 0);
            BottomSheetBehavior.from(panel).setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }

    private void capScroll() {
        if (sheetBinding == null) return;
        sheetBinding.scroll.post(() -> {
            float density = sheetBinding.scroll.getResources().getDisplayMetrics().density;
            int max = Math.round(560 * density);
            if (sheetBinding.scroll.getHeight() > max) {
                sheetBinding.scroll.getLayoutParams().height = max;
                sheetBinding.scroll.requestLayout();
            }
        });
    }

    private Dtos.JobItem find(String id) {
        for (int i = 0; i < getItemCount(); i++) {
            Dtos.JobItem item = getItem(i);
            if (item != null && id.equals(item.id)) return item;
        }
        return null;
    }

    private void load(android.content.Context context, String id) {
        loadingId = id;
        if (sheetBinding != null && id.equals(sheetId)) sheetBinding.progress.setVisibility(View.VISIBLE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.JobItem full = new WorkerRepository(context).job(id);
                main.post(() -> {
                    if (id.equals(loadingId)) loadingId = null;
                    if (full != null) {
                        details.put(id, full);
                        errors.remove(id);
                    }
                    if (sheet != null && sheet.isShowing() && id.equals(sheetId)) paint(full, true);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (id.equals(loadingId)) loadingId = null;
                    errors.put(id, error.getMessage() == null ? "Couldn't load this job." : error.getMessage());
                    Dtos.JobItem shown = details.containsKey(id) ? details.get(id) : find(id);
                    if (sheet != null && sheet.isShowing() && id.equals(sheetId) && shown != null) paint(shown, false);
                });
            }
        });
    }

    private void confirmApply(View view, String id) {
        Dtos.JobItem job = details.containsKey(id) ? details.get(id) : find(id);
        if (job != null && (job.hasApplied || (details.containsKey(id) && !job.eligible))) return;
        Intent intent = new Intent(view.getContext(), ApplyActivity.class);
        intent.putExtra("id", id);
        if (job != null && job.title != null) {
            String place = job.hospitalName == null ? "" : job.hospitalName;
            intent.putExtra("title", place.isEmpty() ? job.title : job.title + " at " + place);
        }
        view.getContext().startActivity(intent);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemJobBinding binding;

        Holder(ItemJobBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
