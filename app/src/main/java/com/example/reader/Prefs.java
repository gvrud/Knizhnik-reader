package com.example.reader;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class Prefs {

    private static final String NAME = "reader_prefs";
    private static final int MAX_BOOKMARKS = 5;

    public static final int BOOKMARK_OK = 1;
    public static final int BOOKMARK_LIMIT = 0;
    public static final int BOOKMARK_DUPLICATE = -1;

    private Prefs() {
    }

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int getFontSize(Context c) {
        return get(c).getInt("font_size", 18);
    }

    public static void setFontSize(Context c, int v) {
        get(c).edit().putInt("font_size", v).commit();
    }

    public static boolean isDark(Context c) {
        return get(c).getBoolean("dark", false);
    }

    public static void setDark(Context c, boolean v) {
        get(c).edit().putBoolean("dark", v).commit();
    }

    public static int getChapter(Context c, String path) {
        return get(c).getInt("chapter_" + path, 0);
    }

    public static void setChapter(Context c, String path, int idx) {
        get(c).edit().putInt("chapter_" + path, idx).commit();
    }

    public static int getPosition(Context c, String path) {
        return get(c).getInt("pos_" + path, 0);
    }

    public static void setPosition(Context c, String path, int offset) {
        get(c).edit().putInt("pos_" + path, offset).commit();
    }

    public static List<int[]> getBookmarks(Context c, String path) {
        String s = get(c).getString("bm_" + path, null);
        List<int[]> out = new ArrayList<int[]>();
        if (s == null || s.length() == 0) {
            return out;
        }
        String[] parts = s.split(";");
        for (String part : parts) {
            int sep = part.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            try {
                int ch = Integer.parseInt(part.substring(0, sep));
                int off = Integer.parseInt(part.substring(sep + 1));
                out.add(new int[]{ch, off});
            } catch (NumberFormatException e) {
                // skip broken entry
            }
        }
        return out;
    }

    public static int addBookmark(Context c, String path, int chapter, int offset) {
        List<int[]> list = getBookmarks(c, path);
        if (list.size() >= MAX_BOOKMARKS) {
            return BOOKMARK_LIMIT;
        }
        for (int[] m : list) {
            if (m[0] == chapter && m[1] == offset) {
                return BOOKMARK_DUPLICATE;
            }
        }
        list.add(new int[]{chapter, offset});
        saveBookmarks(c, path, list);
        return BOOKMARK_OK;
    }

    public static void removeBookmark(Context c, String path, int index) {
        List<int[]> list = getBookmarks(c, path);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveBookmarks(c, path, list);
        }
    }

    private static void saveBookmarks(Context c, String path, List<int[]> list) {
        StringBuilder sb = new StringBuilder();
        for (int[] m : list) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(m[0]).append('|').append(m[1]);
        }
        get(c).edit().putString("bm_" + path, sb.toString()).commit();
    }

    public static boolean isJustify(Context c) {
        return get(c).getBoolean("justify", false);
    }

    public static void setJustify(Context c, boolean v) {
        get(c).edit().putBoolean("justify", v).commit();
    }
}
