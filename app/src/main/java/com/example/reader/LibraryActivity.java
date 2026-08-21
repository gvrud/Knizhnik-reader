package com.example.reader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends Activity {

    private static final int REQ_OPEN = 42;
    private static final int REQ_STORAGE = 100;
    private static final int MENU_OPEN = 1;
    private static final int MENU_RESCAN = 2;
    private static final int MENU_HELP = 3;

    private ListView listView;
    private TextView emptyView;
    private List<File> books = new ArrayList<File>();
    private ArrayAdapter<String> adapter;
    private boolean permissionRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        listView = (ListView) findViewById(R.id.book_list);
        emptyView = (TextView) findViewById(R.id.empty_text);
        adapter = new ArrayAdapter<String>(this, R.layout.item_book, R.id.book_title,
                new ArrayList<String>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openBook(books.get(position));
            }
        });

        if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
            askAllFilesAccess();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < 30
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else {
            scanBooks();
        }
    }

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            return true;
        }
        return Environment.isExternalStorageManager();
    }

    private void askAllFilesAccess() {
        permissionRequested = true;
        new AlertDialog.Builder(this)
                .setMessage(R.string.all_files_access)
                .setPositiveButton(R.string.all_files_open_settings,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionRequested) {
            permissionRequested = false;
            if (hasAllFilesAccess()) {
                scanBooks();
            } else {
                books.clear();
                adapter.clear();
                adapter.notifyDataSetChanged();
                emptyView.setText(R.string.storage_denied);
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanBooks();
            } else {
                books.clear();
                adapter.clear();
                adapter.notifyDataSetChanged();
                emptyView.setText(R.string.storage_denied);
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void scanBooks() {
        final ProgressDialog dlg = ProgressDialog.show(this, null,
                getString(R.string.wait), true, false);
        new AsyncTask<Void, Void, List<File>>() {
            @Override
            protected List<File> doInBackground(Void... params) {
                List<File> result = BookScanner.scan();
                result.addAll(importedBooks());
                return result;
            }

            @Override
            protected void onPostExecute(List<File> result) {
                dlg.dismiss();
                books = result;
                List<String> names = new ArrayList<String>();
                for (File f : books) {
                    names.add(f.getName());
                }
                adapter.clear();
                adapter.addAll(names);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }.execute();
    }

    private List<File> importedBooks() {
        List<File> result = new ArrayList<File>();
        File dir = new File(getFilesDir(), "books");
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File f : files) {
            if (f.isFile()) {
                result.add(f);
            }
        }
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_OPEN, 0, R.string.open_file);
        menu.add(0, MENU_RESCAN, 0, R.string.rescan);
        menu.add(0, MENU_HELP, 0, R.string.help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_OPEN) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(intent, REQ_OPEN);
            } catch (Exception e) {
                toast(R.string.no_file_manager);
            }
            return true;
        }
        if (item.getItemId() == MENU_RESCAN) {
            scanBooks();
            return true;
        }
        if (item.getItemId() == MENU_HELP) {
            showHelp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OPEN && resultCode == RESULT_OK && data != null && data.getData() != null) {
            copyOpenedBook(data.getData());
        }
    }

    private void copyOpenedBook(Uri uri) {
        new AsyncTask<Uri, Void, File>() {
            @Override
            protected File doInBackground(Uri... params) {
                Uri u = params[0];
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    in = getContentResolver().openInputStream(u);
                    if (in == null) {
                        return null;
                    }
                    String name = u.getLastPathSegment();
                    if (name == null || !(name.endsWith(".fb2") || name.endsWith(".epub")
                            || name.endsWith(".mobi"))) {
                        name = "opened.book";
                    }
                    File dir = new File(getFilesDir(), "books");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File f = new File(dir, name);
                    int k = 1;
                    while (f.exists()) {
                        int dot = name.lastIndexOf('.');
                        String base = dot > 0 ? name.substring(0, dot) : name;
                        String ext = dot > 0 ? name.substring(dot) : "";
                        f = new File(dir, base + "_" + k + ext);
                        k++;
                    }
                    out = new FileOutputStream(f);
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        out.write(b, 0, n);
                    }
                    out.flush();
                    return f;
                } catch (Exception e) {
                    return null;
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }

            @Override
            protected void onPostExecute(File f) {
                if (f == null) {
                    toast(R.string.cant_open);
                } else {
                    openBook(f);
                }
            }
        }.execute(uri);
    }

    private void openBook(File f) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("path", f.getAbsolutePath());
        startActivity(intent);
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setMessage(R.string.help_text)
                .setPositiveButton(R.string.help_ok, null)
                .show();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
