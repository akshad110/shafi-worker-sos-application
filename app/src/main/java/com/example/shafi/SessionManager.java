package com.example.shafi;

import android.content.Context;

import com.example.shafi.core.SecureStore;

public final class SessionManager {

    private SessionManager() {
    }

    public static boolean isLoggedIn(Context context) {
        SecureStore.init(context);
        return SecureStore.access() != null;
    }

    public static void logout(Context context) {
        SecureStore.init(context);
        SecureStore.clear();
    }

    public static String getUsername(Context context) {
        SecureStore.init(context);
        String name = SecureStore.name();
        return name == null || name.isEmpty() ? "Worker" : name;
    }
}
