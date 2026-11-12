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
    private static volatile String accessMem;
    private static volatile String refreshMem;
    private static volatile String nameMem = "";
    private static volatile String roleMem = "";
    private static volatile boolean hydrated;

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
            hydrate();
        } catch (Exception error) {
            throw new IllegalStateException("Secure storage could not be opened", error);
        }
    }

    private static SharedPreferences prefs() {
        if (prefs == null) throw new IllegalStateException("Secure storage is not ready");
        return prefs;
    }

    public static void saveSession(String access, String refresh, String name, String role) {
        accessMem = access;
        refreshMem = refresh;
        nameMem = name == null ? "" : name;
        roleMem = role == null ? "" : role;
        hydrated = true;
        prefs().edit()
                .putString(ACCESS, access)
                .putString(REFRESH, refresh)
                .putString(NAME, nameMem)
                .putString(ROLE, roleMem)
                .apply();
    }

    public static void saveName(String name) {
        nameMem = name == null ? "" : name;
        prefs().edit().putString(NAME, nameMem).apply();
    }

    public static String access() {
        ensure();
        return accessMem;
    }

    public static String refresh() {
        ensure();
        return refreshMem;
    }

    public static String name() {
        ensure();
        return nameMem == null ? "" : nameMem;
    }

    public static String role() {
        ensure();
        return roleMem == null ? "" : roleMem;
    }

    public static void clear() {
        accessMem = null;
        refreshMem = null;
        nameMem = "";
        roleMem = "";
        hydrated = true;
        prefs().edit().clear().apply();
    }

    private static void ensure() {
        if (!hydrated) hydrate();
    }

    private static void hydrate() {
        accessMem = prefs().getString(ACCESS, null);
        refreshMem = prefs().getString(REFRESH, null);
        nameMem = prefs().getString(NAME, "");
        roleMem = prefs().getString(ROLE, "");
        hydrated = true;
    }
}
