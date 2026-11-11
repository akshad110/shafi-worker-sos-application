package com.example.shafi.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.example.shafi.R;

public class SkeletonView extends LinearLayout {
    private final ValueAnimator pulse;

    public SkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int pad = dp(4);
        setPadding(pad, pad, pad, pad);
        addBar(0.72f, 16);
        addBar(0.42f, 12);
        addBlock(86);
        addBlock(86);
        addBlock(72);
        pulse = ValueAnimator.ofFloat(0.45f, 1f);
        pulse.setDuration(650);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(animation -> setAlpha((float) animation.getAnimatedValue()));
    }

    private void addBar(float widthFraction, int heightDp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        View bar = new View(getContext());
        bar.setBackgroundResource(R.drawable.bg_skeleton);
        View spacer = new View(getContext());
        row.addView(bar, new LayoutParams(0, dp(heightDp), widthFraction));
        row.addView(spacer, new LayoutParams(0, dp(heightDp), Math.max(0.05f, 1f - widthFraction)));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        addView(row, params);
    }

    private void addBlock(int heightDp) {
        View block = new View(getContext());
        block.setBackgroundResource(R.drawable.bg_skeleton);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(12);
        block.setLayoutParams(params);
        addView(block);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        pulse.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (pulse == null) return;
        if (visibility == VISIBLE && isShown()) pulse.start();
        else pulse.cancel();
    }
}
