package com.example.shafi.ui;

import android.app.Activity;
import android.content.Intent;

import com.example.shafi.MainActivity;
import com.example.shafi.core.AppExecutors;
import com.example.shafi.data.Dtos;
import com.example.shafi.data.WorkerRepository;
import com.example.shafi.ui.screens.OnboardingActivity;

public final class Entry {
    private Entry() {
    }

    public static void afterAuth(Activity activity) {
        AppExecutors.IO.execute(() -> {
            boolean openMain = true;
            try {
                Dtos.ProfileResponse profile = new WorkerRepository(activity).profile();
                openMain = profile != null && profile.canApply;
            } catch (Exception ignored) {
                openMain = true;
            }
            final boolean goMain = openMain;
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(activity, goMain ? MainActivity.class : OnboardingActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                activity.finish();
            });
        });
    }
}
