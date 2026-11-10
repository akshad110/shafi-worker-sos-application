package com.example.shafi.core;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;

public final class SessionBus {
    public static final MutableLiveData<Boolean> expired = new MutableLiveData<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SessionBus() {
    }

    public static void expire() {
        MAIN.post(() -> expired.setValue(true));
    }
}
