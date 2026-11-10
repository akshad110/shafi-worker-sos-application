package com.example.shafi.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SecureStore {
    private static final String FILE = "hospital_sos_secure";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private static final String NAME = "name";
    private static final String ROLE = "role";
    private static SharedPreferences prefs;

    private SecureStore() {
    }

    public static synchronized void init(Context context) {
        if (prefs != null) return;
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception error) {
            throw new IllegalStateException("Secure storage could not be opened", error);
        }
    }

    private static SharedPreferences prefs() {
        if (prefs == null) throw new IllegalStateException("Secure storage is not ready");
        return prefs;
    }

    public static void saveSession(String access, String refresh, String name, String role) {
        prefs().edit()
                .putString(ACCESS, access)
                .putString(REFRESH, refresh)
                .putString(NAME, name)
                .putString(ROLE, role)
                .apply();
    }

    public static void saveName(String name) {
        prefs().edit().putString(NAME, name).apply();
    }

    public static String access() {
        return prefs().getString(ACCESS, null);
    }

    public static String refresh() {
        return prefs().getString(REFRESH, null);
    }

    public static String name() {
        return prefs().getString(NAME, "");
    }

    public static String role() {
        return prefs().getString(ROLE, "");
    }

    public static void clear() {
        prefs().edit().clear().apply();
    }
}
