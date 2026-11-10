package com.example.shafi.core;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.shafi.data.WorkerRepository;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (SecureStore.access() == null) return Result.success();
        try {
            new WorkerRepository(getApplicationContext()).jobs("", "", false, "newest", 1);
            new WorkerRepository(getApplicationContext()).applications();
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }
}
