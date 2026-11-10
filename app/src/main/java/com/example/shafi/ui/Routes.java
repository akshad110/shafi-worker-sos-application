package com.example.shafi.ui;

import android.content.Context;
import android.content.Intent;

import com.example.shafi.data.Dtos;
import com.example.shafi.ui.screens.ApplicationDetailActivity;
import com.example.shafi.ui.screens.DocumentsActivity;
import com.example.shafi.ui.screens.EarningsActivity;
import com.example.shafi.ui.screens.PlacementActivity;
import com.example.shafi.ui.screens.ShiftDetailActivity;

public final class Routes {
    private Routes() {
    }

    public static void open(Context context, Dtos.NoticeData data) {
        if (data == null || data.screen == null) return;
        Intent intent;
        switch (data.screen) {
            case "application":
                intent = new Intent(context, ApplicationDetailActivity.class);
                break;
            case "placement":
                intent = new Intent(context, PlacementActivity.class);
                break;
            case "shift":
                intent = new Intent(context, ShiftDetailActivity.class);
                break;
            case "documents":
                context.startActivity(new Intent(context, DocumentsActivity.class));
                return;
            case "earnings":
                context.startActivity(new Intent(context, EarningsActivity.class));
                return;
            default:
                return;
        }
        intent.putExtra("id", data.entityId);
        context.startActivity(intent);
    }
}
