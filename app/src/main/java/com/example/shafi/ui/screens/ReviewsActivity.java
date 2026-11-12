package com.example.shafi.ui.screens;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shafi.R;
import com.example.shafi.SystemBars;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.core.Format;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.databinding.ActivityReviewsBinding;

import java.util.ArrayList;
import java.util.List;

public class ReviewsActivity extends AppCompatActivity {
    private ActivityReviewsBinding binding;
    private WorkerRepository repository;
    private List<Dtos.ReviewShift> shifts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReviewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this);
        repository = new WorkerRepository(this);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnWrite.setOnClickListener(v -> showWrite());
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvError.setVisibility(View.GONE);
        AppExecutors.IO.execute(() -> {
            try {
                Dtos.ReviewPage page = repository.reviews();
                runOnUiThread(() -> show(page));
            } catch (Exception error) {
                runOnUiThread(() -> fail(error.getMessage()));
            }
        });
    }

    private void show(Dtos.ReviewPage page) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.GONE);
        shifts = page == null || page.shifts == null ? new ArrayList<>() : page.shifts;
        List<Dtos.ReviewItem> items = page == null || page.items == null ? new ArrayList<>() : page.items;
        binding.reviewList.removeAllViews();
        binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (Dtos.ReviewItem item : items) binding.reviewList.addView(card(item));
    }

    private View card(Dtos.ReviewItem item) {
        View row = getLayoutInflater().inflate(R.layout.item_review, binding.reviewList, false);
        boolean mine = "WORKER".equals(item.author);
        String who = mine ? "From you" : "From the hospital";
        String place = item.hospitalName == null || item.hospitalName.isEmpty() ? "Hospital" : item.hospitalName;
        ((TextView) row.findViewById(R.id.tv_author)).setText(mine ? who + " · " + place : who);
        String title = item.jobTitle == null || item.jobTitle.isEmpty() ? place : item.jobTitle;
        ((TextView) row.findViewById(R.id.tv_title)).setText(title);
        ((TextView) row.findViewById(R.id.tv_stars)).setText(stars(item.rating));
        TextView message = row.findViewById(R.id.tv_message);
        if (item.message == null || item.message.trim().isEmpty()) {
            message.setVisibility(View.GONE);
        } else {
            message.setText(item.message.trim());
        }
        String when = Format.prettyStamp(item.createdAt);
        TextView time = row.findViewById(R.id.tv_when);
        time.setText(when == null || when.isEmpty() ? place : place + " · " + when);
        return row;
    }

    private CharSequence stars(int rating) {
        int filled = Math.max(0, Math.min(5, rating));
        SpannableString text = new SpannableString("★★★★★");
        int gold = getColor(R.color.warning);
        int empty = getColor(R.color.text_muted);
        text.setSpan(new ForegroundColorSpan(gold), 0, filled, 0);
        text.setSpan(new ForegroundColorSpan(empty), filled, 5, 0);
        return text;
    }

    private void showWrite() {
        if (shifts.isEmpty()) {
            binding.tvError.setVisibility(View.VISIBLE);
            binding.tvError.setText("Complete a shift before you review a hospital.");
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_review);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        LinearLayout box = dialog.findViewById(R.id.shift_box);
        EditText message = dialog.findViewById(R.id.et_message);
        TextView error = dialog.findViewById(R.id.tv_dialog_error);
        TextView[] starViews = new TextView[]{
                dialog.findViewById(R.id.star_1),
                dialog.findViewById(R.id.star_2),
                dialog.findViewById(R.id.star_3),
                dialog.findViewById(R.id.star_4),
                dialog.findViewById(R.id.star_5)
        };
        final int[] rating = {0};
        final String[] shiftId = {shifts.get(0).id};
        List<TextView> choices = new ArrayList<>();
        for (Dtos.ReviewShift shift : shifts) {
            TextView choice = new TextView(this);
            String label = (shift.hospitalName == null ? "Hospital" : shift.hospitalName)
                    + " · " + (shift.jobTitle == null ? "Shift" : shift.jobTitle);
            if (shift.shiftDate != null && !shift.shiftDate.isEmpty()) label = label + " · " + shift.shiftDate;
            if (shift.reviewed) label = label + " · Update";
            choice.setText(label);
            choice.setTextSize(13);
            choice.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            choice.setLayoutParams(params);
            choices.add(choice);
            box.addView(choice);
            choice.setOnClickListener(v -> {
                shiftId[0] = shift.id;
                paintChoices(choices, shiftId[0]);
            });
        }
        paintChoices(choices, shiftId[0]);
        for (int i = 0; i < starViews.length; i++) {
            int value = i + 1;
            starViews[i].setOnClickListener(v -> {
                rating[0] = value;
                paintStars(starViews, value);
            });
        }
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (rating[0] < 1) {
                error.setVisibility(View.VISIBLE);
                error.setText("Choose a star rating.");
                return;
            }
            error.setVisibility(View.GONE);
            dialog.dismiss();
            binding.progress.setVisibility(View.VISIBLE);
            String note = message.getText().toString().trim();
            AppExecutors.IO.execute(() -> {
                try {
                    Dtos.ReviewPage page = repository.addReview(shiftId[0], rating[0], note);
                    runOnUiThread(() -> show(page));
                } catch (Exception failure) {
                    runOnUiThread(() -> fail(failure.getMessage()));
                }
            });
        });
        dialog.show();
    }

    private void paintChoices(List<TextView> choices, String selected) {
        for (int i = 0; i < choices.size(); i++) {
            boolean on = shifts.get(i).id != null && shifts.get(i).id.equals(selected);
            choices.get(i).setBackgroundResource(on ? R.drawable.bg_chip_filled : R.drawable.bg_chip);
            choices.get(i).setTextColor(getColor(on ? R.color.white : R.color.navy));
        }
    }

    private void paintStars(TextView[] stars, int rating) {
        int gold = getColor(R.color.warning);
        int empty = getColor(R.color.text_muted);
        for (int i = 0; i < stars.length; i++) stars[i].setTextColor(i < rating ? gold : empty);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void fail(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message == null ? "Couldn't load reviews." : message);
    }
}
