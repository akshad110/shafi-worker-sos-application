package com.example.shafi.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.shafi.R;
import com.example.shafi.data.Dtos;
import com.example.shafi.ui.screens.NotificationsActivity;

import java.util.HashSet;
import java.util.Set;

public final class ShiftAlerts {
    private static final String CHANNEL = "shafi_alerts";
    private static final String PREFS = "shafi_alert_ids";

    private ShiftAlerts() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                "Shift alerts",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Offers, shifts, and application updates while the app is closed");
        manager.createNotificationChannel(channel);
    }

    public static void publish(Context context, Dtos.NoticePage page) {
        if (page == null || page.items == null) return;
        ensureChannel(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet("seen", new HashSet<>()));
        boolean primed = prefs.getBoolean("primed", false);
        Set<String> next = new HashSet<>(seen);
        int posted = 0;
        for (Dtos.ActivityItem item : page.items) {
            if (item == null || item.id == null || item.id.isEmpty()) continue;
            next.add(item.id);
            if (!primed || item.read || seen.contains(item.id) || posted >= 3) continue;
            show(context, item);
            posted++;
        }
        prefs.edit().putBoolean("primed", true).putStringSet("seen", next).apply();
    }

    private static void show(Context context, Dtos.ActivityItem item) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent open = new Intent(context, NotificationsActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                item.id.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = item.title == null || item.title.isEmpty() ? "Shafi SOS" : item.title;
        String body = item.body == null ? "" : item.body;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending);
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), builder.build());
    }
}
