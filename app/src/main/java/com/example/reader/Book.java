package com.example.reader;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Book {
    public String filePath;
    public String title;
    public String author;
    public final List<Chapter> chapters = new ArrayList<Chapter>();
    public final Map<String, byte[]> images = new HashMap<String, byte[]>();
    public final Map<String, Bitmap> bitmapCache = new HashMap<String, Bitmap>();
    public final Map<String, String> footnotes = new HashMap<String, String>();
}
