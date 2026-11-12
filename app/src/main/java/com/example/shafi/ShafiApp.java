package com.example.shafi;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.shafi.core.SecureStore;
import com.example.shafi.core.ShiftAlerts;
import com.example.shafi.core.SyncWorker;
import com.example.shafi.data.ApiClient;

import java.util.concurrent.TimeUnit;

public class ShafiApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SecureStore.init(this);
        ApiClient.warm();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        ShiftAlerts.ensureChannel(this);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "hospital-sos-sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }
}
