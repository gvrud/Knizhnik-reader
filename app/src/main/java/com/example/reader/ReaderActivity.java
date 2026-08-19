package com.example.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReaderActivity extends Activity {

    private static final int MIN_FONT = 10;
    private static final int MAX_FONT = 42;
    private static final int HIGHLIGHT = 0xFFFFEB3B;
    private static final int MENU_BOOKMARKS = 1;
    private static final int MENU_HELP = 2;
    private static final int EDGE_ZONE = 20;

    private ScrollView scrollView;
    private TextView textView;
    private TextView chapterLabel;
    private View contentView;
    private LinearLayout topBar;
    private SeekBar pageSeek;
    private LinearLayout root;

    private Book book;
    private int chapterIndex;
    private int fontSize;
    private boolean dark;
    private boolean fullscreen;
    private boolean seekDragging;
    private int pendingOffset;

    private final Html.ImageGetter imageGetter = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            Bitmap bmp = getBookBitmap(source);
            if (bmp == null) {
                return null;
            }
            Drawable d = new BitmapDrawable(getResources(), bmp);
            d.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
            return d;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dark = Prefs.isDark(this);
        setTheme(dark ? android.R.style.Theme_Holo : android.R.style.Theme_Holo_Light_DarkActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        fontSize = Prefs.getFontSize(this);

        root = (LinearLayout) findViewById(R.id.reader_root);
        topBar = (LinearLayout) findViewById(R.id.reader_top_bar);
        scrollView = (ScrollView) findViewById(R.id.scroll_view);
        textView = (TextView) findViewById(R.id.book_text);
        chapterLabel = (TextView) findViewById(R.id.chapter_label);
        contentView = findViewById(R.id.reader_content);
        pageSeek = (SeekBar) findViewById(R.id.page_seek);

        applyTheme();
        applyJustify();

        Button btnToc = (Button) findViewById(R.id.btn_toc);
        Button btnSearch = (Button) findViewById(R.id.btn_search);
        Button btnMinus = (Button) findViewById(R.id.btn_font_minus);
        Button btnPlus = (Button) findViewById(R.id.btn_font_plus);
        Button btnTheme = (Button) findViewById(R.id.btn_theme);
        Button btnJustify = (Button) findViewById(R.id.btn_justify);

        btnToc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToc();
            }
        });
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSearch();
            }
        });
        btnMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeFont(-2);
            }
        });
        btnPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeFont(2);
            }
        });
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });
        btnJustify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleJustify();
            }
        });

        final GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        handleTap(e.getX());
                        return true;
                    }
                });
        contentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                tapDetector.onTouchEvent(event);
                return true;
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        updatePageIndicator();
                        updateSeek();
                    }
                });

        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && seekDragging) {
                    scrollToPage(progress + 1);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekDragging = false;
            }
        });

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            toast(R.string.cant_open);
            finish();
            return;
        }
        loadBook(path);
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_BOOKMARKS, 0, R.string.bookmarks);
        menu.add(0, MENU_HELP, 1, R.string.help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_BOOKMARKS) {
            showBookmarkMenu();
            return true;
        }
        if (item.getItemId() == MENU_HELP) {
            showHelp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyTheme() {
        textView.setTextSize(fontSize);
        if (dark) {
            root.setBackgroundColor(Color.BLACK);
            textView.setTextColor(Color.parseColor("#CCCCCC"));
            textView.setBackgroundColor(Color.BLACK);
        } else {
            root.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.BLACK);
            textView.setBackgroundColor(Color.WHITE);
        }
    }

    private void applyJustify() {
        boolean justify = Prefs.isJustify(this);
        textView.setTypeface(Typeface.DEFAULT);
        if (justify) {
            textView.setGravity(android.view.Gravity.FILL);
        } else {
            textView.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        }
    }

    private void toggleJustify() {
        boolean justify = !Prefs.isJustify(this);
        Prefs.setJustify(this, justify);
        applyJustify();
        toast(justify ? R.string.justify_on : R.string.justify_off);
    }

    private void handleTap(float x) {
        if (book == null) {
            return;
        }
        int w = contentView.getWidth();
        if (w <= 0) {
            return;
        }
        float edge = w * EDGE_ZONE / 100f;
        if (x < edge) {
            pageBack();
        } else if (x > w - edge) {
            pageForward();
        } else {
            toggleFullscreen();
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) {
            topBar.setVisibility(View.GONE);
            pageSeek.setVisibility(View.GONE);
            chapterLabel.setVisibility(View.GONE);
            if (getActionBar() != null) {
                getActionBar().hide();
            }
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            toast(R.string.fullscreen_hint);
        } else {
            topBar.setVisibility(View.VISIBLE);
            pageSeek.setVisibility(View.VISIBLE);
            chapterLabel.setVisibility(View.VISIBLE);
            if (getActionBar() != null) {
                getActionBar().show();
            }
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void pageForward() {
        if (book == null) {
            return;
        }
        int page = currentPage();
        int pages = totalPages();
        if (page < pages) {
            scrollView.smoothScrollTo(0, page * pageHeight());
        } else if (chapterIndex < book.chapters.size() - 1) {
            chapterIndex++;
            displayChapter();
        } else {
            toast(R.string.end_of_book);
        }
    }

    private void pageBack() {
        if (book == null) {
            return;
        }
        int page = currentPage();
        if (page > 1) {
            scrollView.smoothScrollTo(0, (page - 2) * pageHeight());
        } else if (chapterIndex > 0) {
            chapterIndex--;
            displayChapter();
            scrollToBottomLater();
        } else {
            toast(R.string.start_of_book);
        }
    }

    private int pageHeight() {
        int h = scrollView.getHeight();
        return h > 0 ? h : 1;
    }

    private int contentHeight() {
        View child = scrollView.getChildAt(0);
        return child != null ? child.getHeight() : 0;
    }

    private int totalPages() {
        int c = contentHeight();
        int h = pageHeight();
        if (c <= 0) {
            return 1;
        }
        int p = (c + h - 1) / h;
        return p > 0 ? p : 1;
    }

    private int currentPage() {
        int p = scrollView.getScrollY() / pageHeight() + 1;
        return Math.max(1, Math.min(p, totalPages()));
    }

    private void scrollToPage(int page) {
        int pages = totalPages();
        int p = Math.max(1, Math.min(page, pages));
        scrollView.scrollTo(0, (p - 1) * pageHeight());
    }

    private void updatePageIndicator() {
        if (book == null || book.chapters.isEmpty()) {
            return;
        }
        Chapter ch = book.chapters.get(chapterIndex);
        String page = getString(R.string.page_x_of_y, currentPage(), totalPages());
        chapterLabel.setText((chapterIndex + 1) + " / " + book.chapters.size()
                + " — " + ch.title + " · " + page);
    }

    private void updateSeek() {
        if (seekDragging) {
            return;
        }
        int pages = totalPages();
        int cur = currentPage();
        int max = pages > 1 ? pages - 1 : 1;
        if (pageSeek.getMax() != max) {
            pageSeek.setMax(max);
        }
        pageSeek.setProgress(cur - 1);
    }

    private void savePosition() {
        if (book == null) {
            return;
        }
        Prefs.setChapter(this, book.filePath, chapterIndex);
        Prefs.setPosition(this, book.filePath, currentOffset());
    }

    private int currentOffset() {
        Layout layout = textView.getLayout();
        if (layout == null) {
            return 0;
        }
        int line = layout.getLineForVertical(scrollView.getScrollY());
        if (line < 0) {
            line = 0;
        }
        return layout.getLineStart(line);
    }

    private void loadBook(String path) {
        final ProgressDialog dlg = ProgressDialog.show(this, null,
                getString(R.string.wait), true, false);
        final File file = new File(path);
        new AsyncTask<Void, Void, Book>() {
            private String error;

            @Override
            protected Book doInBackground(Void... params) {
                try {
                    return createParser(file.getName()).parse(file);
                } catch (Exception e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Book result) {
                dlg.dismiss();
                if (result == null || result.chapters.isEmpty()) {
                    toast(error != null ? error : getString(R.string.cant_open));
                    finish();
                    return;
                }
                book = result;
                int saved = Prefs.getChapter(ReaderActivity.this, file.getAbsolutePath());
                chapterIndex = saved >= 0 && saved < book.chapters.size() ? saved : 0;
                pendingOffset = Prefs.getPosition(ReaderActivity.this, file.getAbsolutePath());
                displayChapter();
                if (pendingOffset > 0) {
                    scrollToOffset(pendingOffset);
                }
            }
        }.execute();
    }

    private BookParser createParser(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".fb2")) {
            return new Fb2Parser();
        }
        if (n.endsWith(".epub")) {
            return new EpubParser();
        }
        if (n.endsWith(".mobi")) {
            return new MobiParser();
        }
        return new Fb2Parser();
    }

    private void displayChapter() {
        Chapter ch = book.chapters.get(chapterIndex);
        textView.setTextSize(fontSize);
        if (ch.html != null) {
            textView.setText(Html.fromHtml(ch.html, imageGetter, null));
        } else {
            textView.setText(ch.text);
        }
        scrollView.scrollTo(0, 0);
        if (getActionBar() != null) {
            getActionBar().setTitle(book.title);
        }
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                updatePageIndicator();
                updateSeek();
            }
        });
    }

    private void scrollToBottomLater() {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                int h = contentHeight() - pageHeight();
                if (h > 0) {
                    scrollView.scrollTo(0, h);
                }
            }
        });
    }

    private void changeFont(int delta) {
        fontSize = Math.min(MAX_FONT, Math.max(MIN_FONT, fontSize + delta));
        Prefs.setFontSize(this, fontSize);
        textView.setTextSize(fontSize);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                updatePageIndicator();
                updateSeek();
            }
        });
    }

    private void toggleTheme() {
        dark = !dark;
        Prefs.setDark(this, dark);
        recreate();
    }

    private void showToc() {
        if (book == null) {
            return;
        }
        final List<String> titles = new ArrayList<String>();
        for (int i = 0; i < book.chapters.size(); i++) {
            Chapter ch = book.chapters.get(i);
            titles.add(ch.title != null && ch.title.length() > 0
                    ? ch.title : getString(R.string.chapter) + " " + (i + 1));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.toc)
                .setItems(titles.toArray(new String[titles.size()]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                goToChapter(which);
                            }
                        })
                .show();
    }

    private void goToChapter(int idx) {
        if (book == null) {
            return;
        }
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= book.chapters.size()) {
            idx = book.chapters.size() - 1;
        }
        chapterIndex = idx;
        displayChapter();
    }

    private void showSearch() {
        if (book == null) {
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.search_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.search)
                .setView(input)
                .setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doSearch(input.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doSearch(String query) {
        if (query == null || query.trim().length() == 0) {
            return;
        }
        final ProgressDialog dlg = ProgressDialog.show(this, null,
                getString(R.string.wait), true, false);
        final String q = query.trim();
        new AsyncTask<Void, Void, SearchResult>() {
            @Override
            protected SearchResult doInBackground(Void... params) {
                return searchAll(q);
            }

            @Override
            protected void onPostExecute(SearchResult res) {
                dlg.dismiss();
                showSearchResults(q, res);
            }
        }.execute();
    }

    private static class SearchResult {
        List<Integer> chapters = new ArrayList<Integer>();
        List<Integer> offsets = new ArrayList<Integer>();
        long total;
    }

    private SearchResult searchAll(String q) {
        SearchResult res = new SearchResult();
        final String lower = q.toLowerCase(Locale.getDefault());
        final int qlen = q.length();
        for (int i = 0; i < book.chapters.size(); i++) {
            String text = book.chapters.get(i).text.toLowerCase(Locale.getDefault());
            int from = 0;
            while (true) {
                int idx = text.indexOf(lower, from);
                if (idx < 0) {
                    break;
                }
                if (res.chapters.size() < 500) {
                    res.chapters.add(i);
                    res.offsets.add(idx);
                }
                res.total++;
                from = idx + Math.max(1, qlen);
            }
        }
        return res;
    }

    private void showSearchResults(String q, SearchResult res) {
        if (res.chapters.isEmpty()) {
            toast(getString(R.string.not_found) + " \"" + q + "\"");
            return;
        }
        final List<Integer> chs = res.chapters;
        final List<Integer> offs = res.offsets;
        final int qlen = q.length();
        final List<String> items = new ArrayList<String>();
        for (int i = 0; i < chs.size(); i++) {
            int ch = chs.get(i);
            int off = offs.get(i);
            String t = book.chapters.get(ch).text;
            int s = Math.max(0, off - 20);
            int e = Math.min(t.length(), off + qlen + 30);
            String snippet = t.substring(s, e).replace('\n', ' ');
            items.add((ch + 1) + ": " + snippet);
        }
        String title = getString(R.string.results) + ": " + res.total
                + (res.total > chs.size() ? " — " + getString(R.string.first_500) : "");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items.toArray(new String[items.size()]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                jumpToMatch(chs.get(which), offs.get(which), qlen);
                            }
                        })
                .show();
    }

    private void jumpToMatch(int ch, int off, int len) {
        if (book == null) {
            return;
        }
        if (ch != chapterIndex) {
            chapterIndex = ch;
            displayChapter();
        }
        CharSequence current = textView.getText();
        if (current == null || current.length() == 0) {
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(current);
        int start = Math.min(off, sb.length() - 1);
        int end = Math.min(start + len, sb.length());
        sb.setSpan(new BackgroundColorSpan(HIGHLIGHT), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(sb);
        scrollToOffset(start);
    }

    private void scrollToOffset(final int offset) {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                Layout layout = textView.getLayout();
                if (layout == null) {
                    return;
                }
                int textLen = textView.getText().length();
                if (textLen == 0) {
                    return;
                }
                int pos = Math.min(offset, textLen - 1);
                int line = layout.getLineForOffset(pos);
                int y = layout.getLineTop(line);
                scrollView.scrollTo(0, Math.max(0, y - 60));
            }
        });
    }

    private void showBookmarkMenu() {
        if (book == null) {
            return;
        }
        final List<int[]> marks = Prefs.getBookmarks(this, book.filePath);
        final List<String> items = new ArrayList<String>();
        for (int i = 0; i < marks.size(); i++) {
            int[] m = marks.get(i);
            int ci = Math.max(0, Math.min(m[0], book.chapters.size() - 1));
            String t = book.chapters.get(ci).text;
            int s = Math.max(0, m[1] - 15);
            int e = Math.min(t.length(), m[1] + 40);
            String snip = s < t.length() ? t.substring(s, e).replace('\n', ' ') : "";
            items.add((ci + 1) + ": " + snip);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bookmarks) + " (" + marks.size() + "/5)")
                .setPositiveButton(R.string.add_bookmark, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        addBookmarkAtCurrent();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (items.isEmpty()) {
            b.setMessage(R.string.no_bookmarks);
        } else {
            b.setItems(items.toArray(new String[items.size()]),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showBookmarkActions(marks.get(which), which);
                        }
                    });
        }
        b.show();
    }

    private void showBookmarkActions(final int[] mark, final int index) {
        int ci = Math.max(0, Math.min(mark[0], book.chapters.size() - 1));
        String t = book.chapters.get(ci).text;
        int s = Math.max(0, mark[1] - 10);
        int e = Math.min(t.length(), mark[1] + 50);
        String snip = s < t.length() ? t.substring(s, e).replace('\n', ' ') : "";
        new AlertDialog.Builder(this)
                .setTitle(snip)
                .setPositiveButton(R.string.open_bookmark, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        goToBookmark(mark);
                    }
                })
                .setNegativeButton(R.string.delete_bookmark, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.removeBookmark(ReaderActivity.this, book.filePath, index);
                        toast(R.string.bookmark_deleted);
                    }
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void addBookmarkAtCurrent() {
        int off = currentOffset();
        int res = Prefs.addBookmark(this, book.filePath, chapterIndex, off);
        if (res == Prefs.BOOKMARK_OK) {
            toast(R.string.bookmark_added);
        } else if (res == Prefs.BOOKMARK_LIMIT) {
            toast(R.string.bookmark_limit);
        } else {
            toast(R.string.bookmark_exists);
        }
    }

    private void goToBookmark(int[] mark) {
        if (book == null) {
            return;
        }
        int ci = Math.max(0, Math.min(mark[0], book.chapters.size() - 1));
        chapterIndex = ci;
        displayChapter();
        scrollToOffset(mark[1]);
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.help_ok, null)
                .show();
    }

    private Bitmap getBookBitmap(String source) {
        if (book == null || source == null) {
            return null;
        }
        Bitmap cached = book.bitmapCache.get(source);
        if (cached != null) {
            return cached;
        }
        byte[] data = null;
        if (source.startsWith("data:")) {
            data = decodeDataUri(source);
        } else if (source.startsWith("fb2:")) {
            data = book.images.get(source.substring(4));
        } else if (source.startsWith("zip:")) {
            data = book.images.get(source.substring(4));
        }
        if (data == null || data.length == 0) {
            return null;
        }
        Bitmap bmp = decodeScaled(data, getResources().getDisplayMetrics().widthPixels);
        if (bmp != null) {
            book.bitmapCache.put(source, bmp);
        }
        return bmp;
    }

    private byte[] decodeDataUri(String uri) {
        String lower = uri.toLowerCase(Locale.US);
        int comma = uri.indexOf(',');
        if (comma < 0) {
            return null;
        }
        int b64 = lower.indexOf(";base64");
        if (b64 < 0 || b64 >= comma) {
            return null;
        }
        try {
            return Base64.decode(uri.substring(comma + 1).trim(), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeScaled(byte[] data, int maxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int w = bounds.outWidth;
        int h = bounds.outHeight;
        if (w <= 0 || h <= 0) {
            return null;
        }
        int sample = 1;
        while (w / (sample * 2) > maxWidth) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (OutOfMemoryError e) {
            opts.inSampleSize = sample * 2;
            try {
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            } catch (OutOfMemoryError e2) {
                return null;
            }
        }
        if (bmp == null) {
            return null;
        }
        if (bmp.getWidth() > maxWidth && bmp.getWidth() > 0) {
            int nw = maxWidth;
            int nh = Math.max(1, (int) ((long) bmp.getHeight() * maxWidth / bmp.getWidth()));
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true);
                if (scaled != bmp) {
                    bmp.recycle();
                }
                return scaled;
            } catch (OutOfMemoryError e) {
                return bmp;
            }
        }
        return bmp;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
