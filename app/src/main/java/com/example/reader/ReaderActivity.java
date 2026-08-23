package com.example.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Base64;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
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
    private static final int MENU_QUOTES = 3;
    private static final int MENU_FULLSCREEN = 4;
    private static final int MENU_SYNC = 5;
    private static final int MENU_REGISTER = 6;
    private static final int REQ_PICK_DRIVE = 100;
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
    private String stableKey;

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

        Button btnPrevCh = (Button) findViewById(R.id.btn_prev_chapter);
        Button btnNextCh = (Button) findViewById(R.id.btn_next_chapter);
        btnPrevCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToChapter(chapterIndex - 1);
            }
        });
        btnNextCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToChapter(chapterIndex + 1);
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
        menu.add(0, MENU_FULLSCREEN, 0, R.string.fullscreen_menu);
        menu.add(0, MENU_BOOKMARKS, 0, R.string.bookmarks);
        menu.add(0, MENU_QUOTES, 0, R.string.quotes);
        menu.add(0, MENU_SYNC, 0, R.string.sync);
        menu.add(0, MENU_REGISTER, 0, R.string.register_sync);
        menu.add(0, MENU_HELP, 1, R.string.help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_FULLSCREEN) {
            toggleFullscreen();
            return true;
        }
        if (item.getItemId() == MENU_BOOKMARKS) {
            showBookmarkMenu();
            return true;
        }
        if (item.getItemId() == MENU_QUOTES) {
            showQuoteMenu();
            return true;
        }
        if (item.getItemId() == MENU_SYNC) {
            syncCurrentBook();
            return true;
        }
        if (item.getItemId() == MENU_REGISTER) {
            showRegisterDialog();
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            pageForward();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            pageBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
        } else {
            super.onBackPressed();
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
        if (book == null || stableKey == null) {
            return;
        }
        Prefs.setChapter(this, stableKey, chapterIndex);
        Prefs.setPosition(this, stableKey, currentOffset());
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
                    Book parsed = createParser(file.getName()).parse(file);
                    if (parsed != null) {
                        preloadImages(parsed);
                    }
                    return parsed;
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
                stableKey = Prefs.bookKey(file);
                int saved = Prefs.getChapter(ReaderActivity.this, stableKey);
                chapterIndex = saved >= 0 && saved < book.chapters.size() ? saved : 0;
                pendingOffset = Prefs.getPosition(ReaderActivity.this, stableKey);
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
        textView.setTextIsSelectable(true);
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                menu.add(0, 1, 0, R.string.copy);
                menu.add(0, 2, 1, R.string.save_as_quote);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = textView.getSelectionStart();
                int end = textView.getSelectionEnd();
                if (start < 0 || end < 0 || start == end) {
                    mode.finish();
                    return true;
                }
                int s = Math.min(start, end);
                int e = Math.max(start, end);
                String sel = textView.getText().subSequence(s, e).toString();
                if (item.getItemId() == 1) {
                    copyToClipboard(sel);
                    mode.finish();
                    return true;
                }
                if (item.getItemId() == 2) {
                    saveQuote(sel);
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
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

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(book.title, text));
            toast(R.string.copied);
        }
    }

    private void saveQuote(String text) {
        if (stableKey == null) {
            return;
        }
        String quote = text.replace('\n', ' ').trim();
        if (quote.length() == 0) {
            return;
        }
        int res = Prefs.addQuote(this, stableKey, quote);
        if (res == Prefs.BOOKMARK_OK) {
            toast(R.string.quote_saved);
        } else if (res == Prefs.BOOKMARK_LIMIT) {
            toast(R.string.quote_limit);
        } else {
            toast(R.string.quote_exists);
        }
    }

    private void showQuoteMenu() {
        if (book == null || stableKey == null) {
            return;
        }
        final List<String> quotes = Prefs.getQuotes(this, stableKey);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.quotes) + " (" + quotes.size() + "/5)")
                .setNegativeButton(android.R.string.cancel, null);
        if (quotes.isEmpty()) {
            b.setMessage(R.string.no_quotes);
        } else {
            b.setItems(quotes.toArray(new String[quotes.size()]),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showQuoteActions(quotes.get(which), which);
                        }
                    });
        }
        b.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        b.show();
    }

    private void showQuoteActions(final String quote, final int index) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.quote)
                .setMessage(quote)
                .setPositiveButton(R.string.copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyToClipboard(quote);
                    }
                })
                .setNegativeButton(R.string.delete_quote, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.removeQuote(ReaderActivity.this, stableKey, index);
                        toast(R.string.quote_deleted);
                    }
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void showRegisterDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.register_sync)
                .setMessage(R.string.register_hint)
                .setPositiveButton(R.string.select_file, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pickDriveFile();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickDriveFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "knizhnik-sync.json");
        try {
            startActivityForResult(intent, REQ_PICK_DRIVE);
        } catch (Exception e) {
            toast(R.string.no_file_manager);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_DRIVE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            SyncPrefs.setDriveUri(this, data.getData().toString());
            final Uri uri = data.getData();
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    try {
                        if (SyncManager.loadRemoteState(ReaderActivity.this) == null) {
                            SyncManager.saveRemoteState(ReaderActivity.this, "{\"books\":{}}");
                        }
                        return getString(R.string.registered);
                    } catch (Exception e) {
                        return getString(R.string.sync_error) + " " + e.getMessage();
                    }
                }

                @Override
                protected void onPostExecute(String msg) {
                    toast(msg);
                }
            }.execute();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void syncCurrentBook() {
        if (book == null || stableKey == null) {
            return;
        }
        if (!SyncPrefs.isRegistered(this)) {
            toast(R.string.not_registered);
            return;
        }
        final ProgressDialog dlg = ProgressDialog.show(this, null,
                getString(R.string.wait), true, false);
        final String key = stableKey;
        new AsyncTask<Void, Void, String>() {
            private String resultMessage;

            @Override
            protected String doInBackground(Void... params) {
                try {
                    return doSync(key);
                } catch (Exception e) {
                    resultMessage = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String msg) {
                dlg.dismiss();
                if (msg != null) {
                    toast(msg);
                } else if (resultMessage != null) {
                    toast(getString(R.string.sync_error) + " " + resultMessage);
                }
            }
        }.execute();
    }

    private String doSync(String key) throws Exception {
        String remote = SyncManager.loadRemoteState(this);

        int localChapter = Prefs.getChapter(this, key);
        int localPos = Prefs.getPosition(this, key);
        List<int[]> localBm = Prefs.getBookmarks(this, key);
        List<String> localQt = Prefs.getQuotes(this, key);

        boolean hasRemote = false;
        int[] remotePos = null;

        if (remote != null) {
            String bookEntry = extractBookEntry(remote, key);
            if (bookEntry != null) {
                hasRemote = true;
                remotePos = extractChapterPos(bookEntry);
            }
        }

        boolean hasLocal = !localBm.isEmpty() || !localQt.isEmpty() || localPos != 0 || localChapter != 0;
        if (!hasLocal && !hasRemote) {
            return getString(R.string.nothing_to_sync);
        }

        int chapter = localChapter;
        int pos = localPos;
        List<int[]> bookmarks = new ArrayList<int[]>(localBm);
        List<String> quotes = new ArrayList<String>(localQt);

        if (hasRemote) {
            String bookEntry = extractBookEntry(remote, key);
            if (remotePos != null && remotePos[1] > pos) {
                chapter = remotePos[0];
                pos = remotePos[1];
            } else if (!hasLocal && remotePos != null) {
                chapter = remotePos[0];
                pos = remotePos[1];
            }
            List<int[]> remoteBm = extractBookmarks(bookEntry);
            for (int[] m : remoteBm) {
                boolean found = false;
                for (int[] x : bookmarks) {
                    if (x[0] == m[0] && x[1] == m[1]) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    bookmarks.add(m);
                }
            }
            List<String> remoteQt = extractQuotes(bookEntry);
            for (String q : remoteQt) {
                if (!quotes.contains(q)) {
                    quotes.add(q);
                }
            }
        }

        Prefs.applyRemoteState(this, key, chapter, pos, bookmarks, quotes);
        if (chapter != localChapter) {
            chapterIndex = chapter >= 0 && chapter < book.chapters.size() ? chapter : chapterIndex;
            displayChapter();
        }
        if (pos > 0) {
            scrollToOffset(pos);
        }

        String newState = upsertBookEntry(remote, key, chapter, pos, bookmarks, quotes);
        SyncManager.saveRemoteState(this, newState);
        return getString(R.string.sync_done);
    }

    private static String extractBookEntry(String json, String key) {
        int idx = json.indexOf(SyncManager.quote(key));
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int brace = json.indexOf('{', colon);
        if (brace < 0) {
            return null;
        }
        int depth = 0;
        for (int i = brace; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(brace, i + 1);
                }
            }
        }
        return null;
    }

    private static int[] extractChapterPos(String entry) {
        int ci = entry.indexOf("\"chapter\"");
        int pi = entry.indexOf("\"pos\"");
        int[] out = new int[]{0, 0};
        if (ci >= 0) {
            int colon = entry.indexOf(':', ci);
            int end = entry.indexOf(',', colon);
            if (end < 0) {
                end = entry.indexOf('}', colon);
            }
            try {
                out[0] = Integer.parseInt(entry.substring(colon + 1, end).trim());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (pi >= 0) {
            int colon = entry.indexOf(':', pi);
            int end = entry.indexOf(',', colon);
            if (end < 0) {
                end = entry.indexOf('}', colon);
            }
            try {
                out[1] = Integer.parseInt(entry.substring(colon + 1, end).trim());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return out;
    }

    private static List<int[]> extractBookmarks(String entry) {
        List<int[]> out = new ArrayList<int[]>();
        int bi = entry.indexOf("\"bookmarks\"");
        if (bi < 0) {
            return out;
        }
        List<String> arr = SyncManager.splitArray(entry.substring(bi));
        for (String item : arr) {
            String s = SyncManager.unquote(item.trim());
            int sep = s.indexOf('|');
            if (sep > 0) {
                try {
                    int ch = Integer.parseInt(s.substring(0, sep));
                    int off = Integer.parseInt(s.substring(sep + 1));
                    out.add(new int[]{ch, off});
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return out;
    }

    private static List<String> extractQuotes(String entry) {
        List<String> out = new ArrayList<String>();
        int qi = entry.indexOf("\"quotes\"");
        if (qi < 0) {
            return out;
        }
        List<String> arr = SyncManager.splitArray(entry.substring(qi));
        for (String item : arr) {
            String s = SyncManager.unquote(item.trim());
            if (s.length() > 0) {
                out.add(s);
            }
        }
        return out;
    }

    private static String upsertBookEntry(String remote, String key, int chapter, int pos,
            List<int[]> bookmarks, List<String> quotes) {
        StringBuilder entry = new StringBuilder();
        entry.append(SyncManager.quote(key)).append(":{");
        entry.append("\"chapter\":").append(chapter).append(',');
        entry.append("\"pos\":").append(pos).append(',');
        entry.append("\"bookmarks\":[");
        for (int i = 0; i < bookmarks.size(); i++) {
            if (i > 0) {
                entry.append(',');
            }
            int[] m = bookmarks.get(i);
            entry.append(SyncManager.quote(m[0] + "|" + m[1]));
        }
        entry.append("],\"quotes\":[");
        for (int i = 0; i < quotes.size(); i++) {
            if (i > 0) {
                entry.append(',');
            }
            entry.append(SyncManager.quote(quotes.get(i)));
        }
        entry.append("]}");

        if (remote == null) {
            return "{\"books\":{" + entry + "}}";
        }
        int keyIdx = remote.indexOf(SyncManager.quote(key));
        if (keyIdx < 0) {
            int booksIdx = remote.indexOf("\"books\"");
            int brace = remote.indexOf('{', booksIdx);
            if (brace < 0) {
                return remote;
            }
            int depth = 0;
            int insert = -1;
            for (int i = brace; i < remote.length(); i++) {
                char ch = remote.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        insert = i;
                        break;
                    }
                }
            }
            if (insert > 0) {
                return remote.substring(0, insert) + (remote.charAt(insert - 1) == '{' ? "" : ",")
                        + entry + "}";
            }
            return remote;
        }
        int start = remote.lastIndexOf('{', keyIdx);
        int end = findEntryEnd(remote, keyIdx);
        if (start < 0 || end <= start) {
            return remote;
        }
        return remote.substring(0, start) + entry + remote.substring(end);
    }

    private static int findEntryEnd(String json, int keyIdx) {
        int brace = json.indexOf('{', keyIdx);
        if (brace < 0) {
            return json.length();
        }
        int depth = 0;
        for (int i = brace; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return json.length();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.help_ok, null)
                .show();
    }

    private void preloadImages(Book b) {
        int maxWidth = getResources().getDisplayMetrics().widthPixels;
        long totalBytes = 0;
        long limit = 48L * 1024 * 1024;
        int count = 0;
        for (String src : b.images.keySet()) {
            byte[] data = b.images.get(src);
            if (data == null || data.length == 0) {
                continue;
            }
            Bitmap bmp;
            try {
                bmp = decodeScaled(data, maxWidth);
            } catch (OutOfMemoryError e) {
                break;
            }
            if (bmp != null) {
                b.bitmapCache.put("fb2:" + src, bmp);
                b.bitmapCache.put("zip:" + src, bmp);
                totalBytes += bmp.getByteCount();
                count++;
                if (totalBytes > limit || count >= 60) {
                    break;
                }
            }
        }
        if (b.bitmapCache.isEmpty()) {
            preloadDataUris(b, maxWidth, limit);
        }
    }

    private void preloadDataUris(Book b, int maxWidth, long limit) {
        long total = 0;
        int count = 0;
        for (Chapter ch : b.chapters) {
            if (ch.html == null) {
                continue;
            }
            String html = ch.html;
            int i = 0;
            String lower = html.toLowerCase(Locale.US);
            while (true) {
                int idx = lower.indexOf("data:", i);
                if (idx < 0) {
                    break;
                }
                int end = html.indexOf('"', idx);
                if (end < 0) {
                    break;
                }
                String uri = html.substring(idx, end);
                byte[] data = decodeDataUri(uri);
                if (data != null && data.length > 0) {
                    Bitmap bmp;
                    try {
                        bmp = decodeScaled(data, maxWidth);
                    } catch (OutOfMemoryError e) {
                        return;
                    }
                    if (bmp != null) {
                        b.bitmapCache.put(uri, bmp);
                        total += bmp.getByteCount();
                        count++;
                    }
                }
                i = end + 1;
                if (total > limit || count >= 60) {
                    return;
                }
            }
        }
    }

    private Bitmap getBookBitmap(String source) {
        if (book == null || source == null) {
            return null;
        }
        return book.bitmapCache.get(source);
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
        while (h / (sample * 2) > 3000) {
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
