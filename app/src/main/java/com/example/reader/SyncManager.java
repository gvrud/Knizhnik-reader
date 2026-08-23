package com.example.reader;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public final class SyncManager {

    private static final String WEBDAV = "https://webdav.yandex.ru";
    private static final String FOLDER = "Книжник";
    private static final String FILE = "state.json";

    private SyncManager() {
    }

    public static String loadRemoteState(Context c) throws IOException {
        String url = WEBDAV + "/" + encodePath(FOLDER + "/" + FILE);
        HttpURLConnection conn = open(c, url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code == 401) {
                throw new IOException("Неверный логин или пароль");
            }
            if (code != 200) {
                throw new IOException("Ошибка загрузки: " + code);
            }
            return readBody(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    public static void saveRemoteState(Context c, String json) throws IOException {
        ensureFolder(c);
        String url = WEBDAV + "/" + encodePath(FOLDER + "/" + FILE);
        HttpURLConnection conn = open(c, url, "PUT");
        try {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] body = json.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            if (code == 401) {
                throw new IOException("Неверный логин или пароль");
            }
            if (code != 201 && code != 200 && code != 204) {
                throw new IOException("Ошибка сохранения: " + code);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void ensureFolder(Context c) throws IOException {
        String url = WEBDAV + "/" + encodePath(FOLDER);
        HttpURLConnection conn = open(c, url, "MKCOL");
        try {
            int code = conn.getResponseCode();
            if (code == 401) {
                throw new IOException("Неверный логин или пароль");
            }
            // 201 - created, 405 - already exists, 301 - redirect
            if (code != 201 && code != 405 && code != 301) {
                throw new IOException("Ошибка создания папки: " + code);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(Context c, String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Knizhnik/1.7");
        String cred = SyncPrefs.getLogin(c) + ":" + SyncPrefs.getPassword(c);
        conn.setRequestProperty("Authorization",
                "Basic " + Base64.encodeToString(cred.getBytes(), Base64.NO_WRAP));
        try {
            conn.setRequestMethod(method);
        } catch (java.net.ProtocolException e) {
            // MKCOL и другие нестандартные методы требуют рефлексии
            try {
                java.lang.reflect.Method m =
                        HttpURLConnection.class.getMethod("setRequestMethod", String.class);
                m.invoke(conn, method);
            } catch (Exception ex) {
                throw new IOException("Метод " + method + " не поддерживается");
            }
        }
        return conn;
    }

    private static String encodePath(String path) throws IOException {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(p, "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }

    private static String readBody(InputStream in) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(in);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = bis.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    public static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String unquote(String s) {
        if (s == null || s.length() < 2 || s.charAt(0) != '"') {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        int n = s.length() - 1;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < n) {
                char nx = s.charAt(i + 1);
                switch (nx) {
                    case '"': sb.append('"'); i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case 'n': sb.append('\n'); i += 2; continue;
                    case 'r': sb.append('\r'); i += 2; continue;
                    case 't': sb.append('\t'); i += 2; continue;
                    case 'u':
                        if (i + 5 < n) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                i += 6;
                                continue;
                            } catch (NumberFormatException e) {
                                // fallthrough
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    public static List<String> splitArray(String json) {
        List<String> out = new ArrayList<String>();
        if (json == null) {
            return out;
        }
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return out;
        }
        String body = json.substring(start + 1, end);
        int i = 0;
        int len = body.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (body.charAt(i) == ',') {
                i++;
                continue;
            }
            int j = i;
            boolean inStr = false;
            while (j < len) {
                char ch = body.charAt(j);
                if (inStr) {
                    if (ch == '\\') {
                        j += 2;
                        continue;
                    }
                    if (ch == '"') {
                        inStr = false;
                    }
                } else if (ch == '"') {
                    inStr = true;
                } else if (ch == ',') {
                    break;
                }
                j++;
            }
            out.add(body.substring(i, j));
            i = j;
        }
        return out;
    }
}
