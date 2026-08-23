package com.example.reader;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public final class SyncPrefs {

    private static final String NAME = "sync_prefs";

    private SyncPrefs() {
    }

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getLogin(Context c) {
        return get(c).getString("yandex_login", "");
    }

    public static String getPassword(Context c) {
        return get(c).getString("yandex_password", "");
    }

    public static boolean isRegistered(Context c) {
        return getLogin(c).length() > 0 && getPassword(c).length() > 0;
    }

    public static void setCredentials(Context c, String login, String password) {
        get(c).edit().putString("yandex_login", login)
                .putString("yandex_password", password).commit();
    }

    public static String bookKey(File f) {
        return Prefs.bookKey(f);
    }
}
