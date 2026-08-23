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

    public static String getDriveUri(Context c) {
        return get(c).getString("drive_uri", "");
    }

    public static boolean isRegistered(Context c) {
        return getDriveUri(c).length() > 0;
    }

    public static void setDriveUri(Context c, String uri) {
        get(c).edit().putString("drive_uri", uri).commit();
    }

    public static String bookKey(File f) {
        return Prefs.bookKey(f);
    }
}
