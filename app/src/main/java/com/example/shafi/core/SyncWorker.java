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
            WorkerRepository repository = new WorkerRepository(getApplicationContext());
            repository.jobs("", "", false, "newest", 1);
            repository.applications();
            ShiftAlerts.publish(getApplicationContext(), repository.notifications());
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }
}
