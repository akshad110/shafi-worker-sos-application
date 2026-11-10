package com.example.shafi;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class SystemBars {

    private SystemBars() {
    }

    public static void apply(Activity activity) {
        apply(activity, false);
    }

    public static void apply(Activity activity, boolean liftForKeyboard) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        int bar = ContextCompat.getColor(activity, R.color.white);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(bar);
        window.setNavigationBarColor(bar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        ViewGroup content = activity.findViewById(android.R.id.content);
        content.setBackgroundColor(ContextCompat.getColor(activity, R.color.page));
        if (content.getChildCount() == 0) {
            return;
        }

        View screen = content.getChildAt(0);
        ViewCompat.setOnApplyWindowInsetsListener(screen, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = bars.bottom;
            if (liftForKeyboard) {
                int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                if (keyboard > bottom) {
                    bottom = keyboard;
                }
            }
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp.topMargin != bars.top
                    || lp.bottomMargin != bottom
                    || lp.leftMargin != bars.left
                    || lp.rightMargin != bars.right) {
                lp.topMargin = bars.top;
                lp.bottomMargin = bottom;
                lp.leftMargin = bars.left;
                lp.rightMargin = bars.right;
                v.setLayoutParams(lp);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(screen);
    }
}
