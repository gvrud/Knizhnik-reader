package com.example.reader;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BookScanner {

    private static final String[] EXT = {".fb2", ".epub", ".mobi"};

    private BookScanner() {
    }

    public static List<File> scan() {
        List<File> result = new ArrayList<File>();
        Set<String> seen = new HashSet<String>();
        File root = Environment.getExternalStorageDirectory();
        if (root != null && root.isDirectory()) {
            walk(root, 0, seen, result);
        }
        sort(result);
        return result;
    }

    private static void walk(File dir, int depth, Set<String> seen, List<File> out) {
        if (depth > 3) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String key;
            try {
                key = f.getCanonicalPath();
            } catch (IOException e) {
                key = f.getAbsolutePath();
            }
            if (key == null || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase(Locale.US);
                if (!n.startsWith(".")) {
                    walk(f, depth + 1, seen, out);
                }
            } else if (isBook(f.getName())) {
                out.add(f);
            }
        }
    }

    private static boolean isBook(String name) {
        String n = name.toLowerCase(Locale.US);
        for (String e : EXT) {
            if (n.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private static void sort(List<File> list) {
        File[] arr = list.toArray(new File[list.size()]);
        Arrays.sort(arr, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        list.clear();
        for (File f : arr) {
            list.add(f);
        }
    }
}
