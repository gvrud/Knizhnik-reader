package com.example.reader;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EpubParser implements BookParser {

    @Override
    public Book parse(File file) throws IOException {
        Book book = new Book();
        book.filePath = file.getAbsolutePath();
        book.title = file.getName();
        book.author = "";

        Map<String, byte[]> entries = new HashMap<String, byte[]>();
        readZip(file, entries);

        String container = decodeUtf8(getBytes(entries, "META-INF/container.xml"));
        if (container == null) {
            throw new IOException("Это не EPUB: нет container.xml");
        }
        String opfPath = findOpfPath(container);
        if (opfPath == null) {
            throw new IOException("Не найден OPF-файл");
        }

        byte[] opfBytes = getBytes(entries, opfPath);
        if (opfBytes == null) {
            throw new IOException("Не найден OPF: " + opfPath);
        }

        OpfData opf = parseOpf(decodeUtf8(opfBytes));
        if (opf.spine.isEmpty()) {
            throw new IOException("Пустой spine в EPUB");
        }

        List<TocEntry> toc = null;
        if (opf.tocId != null) {
            String ncxHref = opf.manifest.get(opf.tocId);
            if (ncxHref != null) {
                byte[] ncxBytes = getBytes(entries, resolvePath(opfPath, ncxHref));
                if (ncxBytes != null) {
                    toc = parseNcx(decodeUtf8(ncxBytes));
                }
            }
        }

        int n = 1;
        for (String idref : opf.spine) {
            String href = opf.manifest.get(idref);
            if (href == null) {
                continue;
            }
            String path = resolvePath(opfPath, href);
            byte[] content = getBytes(entries, path);
            if (content == null) {
                continue;
            }
            String raw = decodeUtf8(content);
            String html = extractBody(stripBlocks(raw, new String[]{"script", "style"}));
            html = rewriteImages(html, path, entries, book);
            String text = HtmlUtil.toPlainText(html);
            text = HtmlUtil.collapseWhitespace(text);
            if (text.length() == 0) {
                continue;
            }
            String title = findTocTitle(toc, href);
            if (title == null || title.length() == 0) {
                title = "Глава " + n;
            }
            Chapter ch = new Chapter(title, text);
            ch.html = html;
            book.chapters.add(ch);
            n++;
        }
        if (book.chapters.isEmpty()) {
            throw new IOException("В EPUB не найден текст");
        }

        if (opf.title != null && opf.title.length() > 0) {
            book.title = opf.title;
        }
        if (opf.creator != null && opf.creator.length() > 0) {
            book.author = opf.creator;
        }
        return book;
    }

    private static class OpfData {
        String title;
        String creator;
        String tocId;
        final Map<String, String> manifest = new HashMap<String, String>();
        final List<String> spine = new ArrayList<String>();
    }

    private static class TocEntry {
        final String title;
        final String src;

        TocEntry(String title, String src) {
            this.title = title;
            this.src = src;
        }
    }

    private static OpfData parseOpf(String xml) throws IOException {
        OpfData data = new OpfData();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(xml));
            String capture = null;
            StringBuilder buf = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                String local = localName(p.getName());
                if (ev == XmlPullParser.START_TAG) {
                    if ("title".equals(local)) {
                        capture = "title";
                        buf = new StringBuilder();
                    } else if ("creator".equals(local)) {
                        capture = "creator";
                        buf = new StringBuilder();
                    } else if ("item".equals(local)) {
                        String id = p.getAttributeValue(null, "id");
                        String href = p.getAttributeValue(null, "href");
                        String mt = p.getAttributeValue(null, "media-type");
                        if (id != null && href != null) {
                            data.manifest.put(id, href);
                        }
                        if (id != null && "application/x-dtbncx+xml".equals(mt)) {
                            data.tocId = id;
                        }
                    } else if ("itemref".equals(local)) {
                        String idref = p.getAttributeValue(null, "idref");
                        if (idref != null) {
                            data.spine.add(idref);
                        }
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (capture != null && buf != null) {
                        buf.append(p.getText());
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("title".equals(local) && "title".equals(capture)) {
                        data.title = buf == null ? null : buf.toString().trim();
                        capture = null;
                        buf = null;
                    } else if ("creator".equals(local) && "creator".equals(capture)) {
                        data.creator = buf == null ? null : buf.toString().trim();
                        capture = null;
                        buf = null;
                    }
                }
                ev = p.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка разбора OPF: " + e.getMessage());
        }
        return data;
    }

    private static List<TocEntry> parseNcx(String xml) throws IOException {
        List<TocEntry> out = new ArrayList<TocEntry>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(xml));
            boolean inText = false;
            StringBuilder buf = null;
            String curSrc = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                String local = localName(p.getName());
                if (ev == XmlPullParser.START_TAG) {
                    if ("text".equals(local)) {
                        inText = true;
                        buf = new StringBuilder();
                    } else if ("content".equals(local)) {
                        curSrc = p.getAttributeValue(null, "src");
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    if (inText && buf != null) {
                        buf.append(p.getText());
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("text".equals(local)) {
                        inText = false;
                    } else if ("navPoint".equals(local)) {
                        String title = buf == null ? "" : buf.toString().trim();
                        out.add(new TocEntry(title, curSrc));
                        buf = null;
                        curSrc = null;
                    }
                }
                ev = p.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка разбора NCX: " + e.getMessage());
        }
        return out;
    }

    private static String findOpfPath(String xml) throws IOException {
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(xml));
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "rootfile".equals(localName(p.getName()))) {
                    String path = p.getAttributeValue(null, "full-path");
                    if (path != null) {
                        return path;
                    }
                }
                ev = p.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка разбора container.xml: " + e.getMessage());
        }
        return null;
    }

    private static String findTocTitle(List<TocEntry> toc, String href) {
        if (toc == null) {
            return null;
        }
        for (TocEntry e : toc) {
            if (e.src == null) {
                continue;
            }
            String src = stripFragment(e.src);
            if (src.equalsIgnoreCase(href)) {
                return e.title;
            }
            String h1 = stripDir(href);
            String s1 = stripDir(src);
            if (h1 != null && h1.equalsIgnoreCase(s1)) {
                return e.title;
            }
        }
        return null;
    }

    private static String stripFragment(String src) {
        String s = src.trim();
        int frag = s.indexOf('#');
        if (frag >= 0) {
            s = s.substring(0, frag);
        }
        if (s.indexOf('%') >= 0) {
            try {
                s = URLDecoder.decode(s, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        return s;
    }

    private static String stripDir(String path) {
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            return path.substring(slash + 1);
        }
        return path;
    }

    private static String localName(String name) {
        if (name == null) {
            return null;
        }
        int idx = name.lastIndexOf(':');
        if (idx >= 0) {
            return name.substring(idx + 1);
        }
        return name;
    }

    private static String resolvePath(String base, String href) {
        if (href == null) {
            return null;
        }
        String h = href.trim();
        int frag = h.indexOf('#');
        if (frag >= 0) {
            h = h.substring(0, frag);
        }
        if (h.length() == 0) {
            return base;
        }
        String dir = "";
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            dir = base.substring(0, slash + 1);
        }
        String path = dir + h;
        if (path.indexOf('%') >= 0) {
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        String[] parts = path.split("/");
        List<String> stack = new ArrayList<String>();
        for (String part : parts) {
            if (part.length() == 0 || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            stack.add(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : stack) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String extractBody(String s) {
        String lower = s.toLowerCase(Locale.US);
        int b = lower.indexOf("<body");
        if (b < 0) {
            return s;
        }
        int gt = lower.indexOf('>', b);
        if (gt < 0) {
            return s;
        }
        int end = lower.indexOf("</body", gt + 1);
        if (end < 0) {
            return s.substring(gt + 1);
        }
        return s.substring(gt + 1, end);
    }

    private static String stripBlocks(String s, String[] tags) {
        String result = s;
        for (String t : tags) {
            StringBuilder sb = new StringBuilder();
            String lower = result.toLowerCase(Locale.US);
            String open = "<" + t;
            String close = "</" + t + ">";
            int i = 0;
            while (i < result.length()) {
                int s1 = lower.indexOf(open, i);
                if (s1 < 0) {
                    sb.append(result, i, result.length());
                    break;
                }
                int e1 = lower.indexOf('>', s1);
                if (e1 < 0) {
                    sb.append(result, i, result.length());
                    break;
                }
                int e2 = lower.indexOf(close, e1 + 1);
                if (e2 < 0) {
                    sb.append(result, i, result.length());
                    break;
                }
                sb.append(result, i, s1);
                i = e2 + close.length();
            }
            result = sb.toString();
        }
        return result;
    }

    private static String rewriteImages(String html, String basePath,
            Map<String, byte[]> entries, Book book) {
        StringBuilder out = new StringBuilder(html.length() + 64);
        String lower = html.toLowerCase(Locale.US);
        int i = 0;
        while (i < html.length()) {
            int tag = lower.indexOf("<img", i);
            if (tag < 0) {
                out.append(html, i, html.length());
                break;
            }
            int tagEnd = lower.indexOf('>', tag + 4);
            if (tagEnd < 0) {
                out.append(html, i, html.length());
                break;
            }
            out.append(html, i, tag);
            out.append(rewriteOneImg(html.substring(tag, tagEnd + 1), basePath, entries, book));
            i = tagEnd + 1;
        }
        return out.toString();
    }

    private static String rewriteOneImg(String tagText, String basePath,
            Map<String, byte[]> entries, Book book) {
        String lower = tagText.toLowerCase(Locale.US);
        int si = lower.indexOf("src");
        if (si < 0) {
            return tagText;
        }
        int j = si + 3;
        while (j < tagText.length() && Character.isWhitespace(tagText.charAt(j))) {
            j++;
        }
        if (j >= tagText.length() || tagText.charAt(j) != '=') {
            return tagText;
        }
        j++;
        while (j < tagText.length() && Character.isWhitespace(tagText.charAt(j))) {
            j++;
        }
        if (j >= tagText.length()) {
            return tagText;
        }
        char q = tagText.charAt(j);
        if (q != '"' && q != '\'') {
            return tagText;
        }
        int end = tagText.indexOf(q, j + 1);
        if (end < 0) {
            return tagText;
        }
        String value = HtmlUtil.unescape(tagText.substring(j + 1, end).trim());
        if (value.startsWith("data:")) {
            return tagText;
        }
        String path = resolvePath(basePath, value);
        byte[] bytes = getBytes(entries, path);
        if (bytes == null) {
            return tagText;
        }
        book.images.put(path, bytes);
        return tagText.substring(0, j + 1) + "zip:" + path + tagText.substring(end);
    }

    private static void readZip(File file, Map<String, byte[]> out) throws IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(file));
        try {
            ZipEntry e;
            byte[] buf = new byte[32768];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                int size = e.getSize() > 0 ? (int) Math.min(e.getSize(), 100 * 1024 * 1024) : 32768;
                ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(4096, size));
                int n;
                while ((n = zin.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                out.put(e.getName(), bos.toByteArray());
            }
        } finally {
            zin.close();
        }
    }

    private static byte[] getBytes(Map<String, byte[]> entries, String path) {
        if (path == null) {
            return null;
        }
        byte[] b = entries.get(path);
        if (b != null) {
            return b;
        }
        for (Map.Entry<String, byte[]> en : entries.entrySet()) {
            if (en.getKey().equalsIgnoreCase(path)) {
                return en.getValue();
            }
        }
        return null;
    }

    private static String decodeUtf8(byte[] b) {
        if (b == null) {
            return null;
        }
        int off = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            off = 3;
        }
        try {
            return new String(b, off, b.length - off, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(b);
        }
    }
}
