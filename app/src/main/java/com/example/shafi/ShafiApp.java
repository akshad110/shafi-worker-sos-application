package com.example.shafi;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.shafi.core.SecureStore;
import com.example.shafi.core.SyncWorker;

import java.util.concurrent.TimeUnit;

public class ShafiApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SecureStore.init(this);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "hospital-sos-sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }
}
