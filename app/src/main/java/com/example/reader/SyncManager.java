package com.example.reader;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class SyncManager {

    private SyncManager() {
    }

    public static String loadRemoteState(Context c) throws IOException {
        String uriStr = SyncPrefs.getDriveUri(c);
        if (uriStr.length() == 0) {
            return null;
        }
        Uri uri = Uri.parse(uriStr);
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            throw new IOException("Не удалось открыть файл синхронизации: " + e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static void saveRemoteState(Context c, String json) throws IOException {
        String uriStr = SyncPrefs.getDriveUri(c);
        if (uriStr.length() == 0) {
            throw new IOException("Файл синхронизации не выбран");
        }
        Uri uri = Uri.parse(uriStr);
        OutputStream out = null;
        try {
            out = c.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                throw new IOException("Не удалось открыть файл для записи");
            }
            byte[] body = json.getBytes("UTF-8");
            out.write(body);
            out.flush();
        } catch (Exception e) {
            throw new IOException("Не удалось сохранить: " + e.getMessage());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
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
