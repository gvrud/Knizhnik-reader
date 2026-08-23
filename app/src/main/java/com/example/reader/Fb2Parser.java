package com.example.reader;

import android.util.Base64;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fb2Parser implements BookParser {

    private static final Map<String, String> GREEK = new HashMap<String, String>();

    static {
        String[][] g = {
            {"Alpha","\u0391"},{"Beta","\u0392"},{"Gamma","\u0393"},{"Delta","\u0394"},
            {"Epsilon","\u0395"},{"Zeta","\u0396"},{"Eta","\u0397"},{"Theta","\u0398"},
            {"Iota","\u0399"},{"Kappa","\u039A"},{"Lambda","\u039B"},{"Mu","\u039C"},
            {"Nu","\u039D"},{"Xi","\u039E"},{"Omicron","\u039F"},{"Pi","\u03A0"},
            {"Rho","\u03A1"},{"Sigma","\u03A3"},{"Tau","\u03A4"},{"Upsilon","\u03A5"},
            {"Phi","\u03A6"},{"Chi","\u03A7"},{"Psi","\u03A8"},{"Omega","\u03A9"},
            {"alpha","\u03B1"},{"beta","\u03B2"},{"gamma","\u03B3"},{"delta","\u03B4"},
            {"epsilon","\u03B5"},{"zeta","\u03B6"},{"eta","\u03B7"},{"theta","\u03B8"},
            {"iota","\u03B9"},{"kappa","\u03BA"},{"lambda","\u03BB"},{"mu","\u03BC"},
            {"nu","\u03BD"},{"xi","\u03BE"},{"omicron","\u03BF"},{"pi","\u03C0"},
            {"rho","\u03C1"},{"sigmaf","\u03C2"},{"sigma","\u03C3"},{"tau","\u03C4"},
            {"upsilon","\u03C5"},{"phi","\u03C6"},{"chi","\u03C7"},{"psi","\u03C8"},
            {"omega","\u03C9"},{"thetasym","\u03D1"},{"upsih","\u03D2"},{"piv","\u03D6"},
        };
        for (String[] pair : g) {
            GREEK.put(pair[0].toLowerCase(Locale.US), pair[1]);
        }
    }

    private static final Pattern ENCODING_PATTERN =
            Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    @Override
    public Book parse(File file) throws IOException {
        Book book = new Book();
        book.filePath = file.getAbsolutePath();
        book.title = file.getName();
        book.author = "";

        String charset = detectCharset(file);

        try {
            if (hasGreekEntities(file, charset)) {
                String xml = readAndPreprocess(file);
                parseXml(book, xml, file.getName());
            } else {
                parseXmlStreaming(book, file, charset, file.getName());
            }
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка разбора FB2: " + e.getMessage());
        }
        return book;
    }

    private static boolean hasGreekEntities(File file, String charset) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[65536];
            int n = in.read(buf);
            String s = new String(buf, 0, Math.max(0, n), charset);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '&') {
                    int semi = s.indexOf(';', i);
                    if (semi > i && semi - i <= 14) {
                        String name = s.substring(i + 1, semi).toLowerCase(Locale.US);
                        if (GREEK.containsKey(name)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } finally {
            in.close();
        }
    }

    private void parseXmlStreaming(Book book, File file, String charset, String fileName)
            throws IOException, XmlPullParserException {
        InputStream in = new FileInputStream(file);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, charset);
            runParser(book, parser, fileName);
        } finally {
            in.close();
        }
    }

    private void parseXml(Book book, String xml, String fileName) throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xml));
        runParser(book, parser, fileName);
    }

    private static String readAndPreprocess(File file) throws IOException {
        String charset = detectCharset(file);
        InputStream in = new FileInputStream(file);
        java.io.Reader reader = null;
        try {
            String enc = charset;
            int skip = 0;
            if ("UTF-8".equals(enc)) {
                byte[] b = new byte[3];
                int n = in.read(b);
                if (n >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
                    skip = 3;
                } else {
                    in.close();
                    in = new FileInputStream(file);
                }
            } else if ("UTF-16LE".equals(enc) || "UTF-16BE".equals(enc)) {
                in.close();
                in = new FileInputStream(file);
            }
            reader = new InputStreamReader(in, enc);
            StringBuilder sb = new StringBuilder((int) Math.min(file.length(), 64 * 1024 * 1024));
            char[] buf = new char[32768];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            String xml = sb.toString();
            return cleanXml(xml);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            } else if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static String detectCharset(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            byte[] head = new byte[512];
            int n = in.read(head);
            if (n >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
                return "UTF-8";
            }
            if (n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
                return "UTF-16LE";
            }
            if (n >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
                return "UTF-16BE";
            }
            String s;
            try {
                s = new String(head, 0, n, "ISO-8859-1");
            } catch (Exception e) {
                return "UTF-8";
            }
            Matcher m = ENCODING_PATTERN.matcher(s);
            if (m.find()) {
                return mapCharset(m.group(1).trim().toLowerCase(Locale.US));
            }
            return "UTF-8";
        } finally {
            in.close();
        }
    }

    private void runParser(Book book, XmlPullParser parser, String fileName)
            throws IOException, XmlPullParserException {
        String authorFirst = "";
        String authorLast = "";

        Chapter current = null;
        StringBuilder curHtml = null;
        StringBuilder bodyHtml = new StringBuilder();
        StringBuilder paraBuf = null;
        StringBuilder titleBuf = null;
        StringBuilder metaBuf = null;
        StringBuilder binaryBuf = null;
        String binaryId = null;
        boolean inSectionTitle = false;
        boolean captureMeta = false;
        boolean skipBody = false;
        String metaTag = null;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("body".equals(name)) {
                    String bname = parser.getAttributeValue(null, "name");
                    skipBody = bname != null
                            && ("notes".equals(bname) || "footnotes".equals(bname) || "comments".equals(bname));
                } else if (skipBody) {
                    // ignore content of notes bodies
                } else if ("section".equals(name)) {
                    flushChapter(book, current);
                    current = new Chapter("", "");
                    curHtml = new StringBuilder();
                } else if ("title".equals(name)) {
                    if (current != null) {
                        inSectionTitle = true;
                        titleBuf = new StringBuilder();
                    }
                } else if ("binary".equals(name)) {
                    binaryId = parser.getAttributeValue(null, "id");
                    binaryBuf = new StringBuilder();
                } else if ("book-title".equals(name) || "first-name".equals(name)
                        || "middle-name".equals(name) || "last-name".equals(name)) {
                    captureMeta = true;
                    metaTag = name;
                    metaBuf = new StringBuilder();
                } else if ("p".equals(name) || "v".equals(name)) {
                    paraBuf = new StringBuilder();
                } else if ("empty-line".equals(name)) {
                    appendHtml(current, curHtml, bodyHtml, "<br />");
                } else if ("strong".equals(name)) {
                    appendHtml(current, curHtml, bodyHtml, "<b>");
                } else if ("emphasis".equals(name)) {
                    appendHtml(current, curHtml, bodyHtml, "<i>");
                } else if ("image".equals(name)) {
                    String id = findHref(parser);
                    if (id != null && id.length() > 0) {
                        appendHtml(current, curHtml, bodyHtml, "<img src=\"fb2:" + id + "\" />");
                    }
                }
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                String text = parser.getText();
                if (text == null) {
                    text = "";
                }
                if (captureMeta && metaBuf != null) {
                    metaBuf.append(text);
                } else if (binaryBuf != null) {
                    binaryBuf.append(text);
                } else if (!skipBody) {
                    if (inSectionTitle && titleBuf != null) {
                        titleBuf.append(text);
                    } else if (paraBuf != null) {
                        paraBuf.append(text);
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("body".equals(name)) {
                    skipBody = false;
                } else if (skipBody) {
                    // ignore
                } else if ("p".equals(name) || "v".equals(name)) {
                    if (paraBuf != null) {
                        appendHtml(current, curHtml, bodyHtml,
                                "<p>" + HtmlUtil.escape(paraBuf.toString()) + "</p>");
                        paraBuf = null;
                    }
                } else if ("strong".equals(name)) {
                    appendHtml(current, curHtml, bodyHtml, "</b>");
                } else if ("emphasis".equals(name)) {
                    appendHtml(current, curHtml, bodyHtml, "</i>");
                } else if ("title".equals(name)) {
                    if (inSectionTitle && current != null && titleBuf != null) {
                        current.title = titleBuf.toString().trim();
                    }
                    inSectionTitle = false;
                    titleBuf = null;
                } else if ("binary".equals(name)) {
                    if (binaryId != null && binaryBuf != null) {
                        try {
                            byte[] data = Base64.decode(binaryBuf.toString().trim(), Base64.DEFAULT);
                            if (data != null && data.length > 0) {
                                book.images.put(binaryId, data);
                            }
                        } catch (Exception e) {
                            // broken image, ignore
                        }
                    }
                    binaryId = null;
                    binaryBuf = null;
                } else if ("section".equals(name)) {
                    if (current != null) {
                        current.html = curHtml == null ? "" : curHtml.toString();
                        current.text = HtmlUtil.collapseWhitespace(HtmlUtil.toPlainText(current.html));
                    }
                    flushChapter(book, current);
                    current = null;
                    curHtml = null;
                } else if (captureMeta && metaBuf != null
                        && ("book-title".equals(name) || "first-name".equals(name)
                            || "middle-name".equals(name) || "last-name".equals(name))) {
                    if ("title".equals(metaTag)) {
                        book.title = metaBuf.toString().trim();
                    } else if ("first-name".equals(metaTag)) {
                        authorFirst = metaBuf.toString().trim();
                    } else if ("middle-name".equals(metaTag)) {
                        authorFirst = authorFirst.length() > 0
                                ? authorFirst + " " + metaBuf.toString().trim()
                                : metaBuf.toString().trim();
                    } else if ("last-name".equals(metaTag)) {
                        authorLast = metaBuf.toString().trim();
                    }
                    captureMeta = false;
                    metaBuf = null;
                }
            }
            event = parser.next();
        }

        if (current != null) {
            current.html = curHtml == null ? "" : curHtml.toString();
            current.text = HtmlUtil.collapseWhitespace(HtmlUtil.toPlainText(current.html));
        }
        flushChapter(book, current);

        if (book.chapters.isEmpty() && bodyHtml.length() > 0) {
            Chapter whole = new Chapter("", "");
            whole.html = bodyHtml.toString();
            whole.text = HtmlUtil.collapseWhitespace(HtmlUtil.toPlainText(whole.html));
            flushChapter(book, whole);
        }

        for (int i = 0; i < book.chapters.size(); i++) {
            Chapter ch = book.chapters.get(i);
            if (ch.title == null || ch.title.length() == 0) {
                ch.title = "Глава " + (i + 1);
            }
        }

        StringBuilder auth = new StringBuilder();
        if (authorFirst.length() > 0) {
            auth.append(authorFirst);
        }
        if (authorLast.length() > 0) {
            if (auth.length() > 0) {
                auth.append(' ');
            }
            auth.append(authorLast);
        }
        book.author = auth.toString();

        if (book.title == null || book.title.length() == 0) {
            book.title = fileName;
        }
    }

    private static String mapCharset(String enc) {
        if (enc.contains("1251")) {
            return "CP1251";
        }
        if (enc.contains("koi8")) {
            return "KOI8-R";
        }
        if (enc.contains("1252")) {
            return "CP1252";
        }
        if (enc.contains("8859-5")) {
            return "ISO-8859-5";
        }
        if (enc.contains("utf-16le")) {
            return "UTF-16LE";
        }
        if (enc.contains("utf-16be")) {
            return "UTF-16BE";
        }
        if (enc.contains("utf-16")) {
            return "UTF-16";
        }
        if (enc.contains("utf-8")) {
            return "UTF-8";
        }
        if (enc.contains("8859-1") || enc.contains("latin")) {
            return "ISO-8859-1";
        }
        return "UTF-8";
    }

    private static void appendHtml(Chapter current, StringBuilder curHtml, StringBuilder bodyHtml, String s) {
        if (current != null && curHtml != null) {
            curHtml.append(s);
        } else {
            bodyHtml.append(s);
        }
    }

    private static String findHref(XmlPullParser p) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String n = p.getAttributeName(i);
            if (n != null && (n.endsWith("href") || n.endsWith("Href"))) {
                String v = p.getAttributeValue(i);
                if (v == null) {
                    continue;
                }
                if (v.startsWith("#")) {
                    return v.substring(1);
                }
                return v;
            }
        }
        return null;
    }

    private static void flushChapter(Book book, Chapter ch) {
        if (ch == null) {
            return;
        }
        if (ch.text != null && ch.text.length() > 0) {
            splitChapter(book, ch);
        }
    }

    private static void splitChapter(Book book, Chapter ch) {
        final int MAX_CHARS = 60000;
        final int MAX_IMAGES = 15;
        if (ch.html == null || ch.html.length() < MAX_CHARS / 2) {
            book.chapters.add(ch);
            return;
        }
        String html = ch.html;
        String text = ch.text;
        List<String> parts = new ArrayList<String>();
        int from = 0;
        int count = 0;
        int lastCut = 0;
        for (int i = 0; i < html.length(); i++) {
            if (html.charAt(i) == '<' && i + 3 < html.length()
                    && html.startsWith("<img", i)) {
                count++;
            }
            if (i - from >= MAX_CHARS || count >= MAX_IMAGES) {
                parts.add(html.substring(from, i + 1));
                from = i + 1;
                count = 0;
                lastCut = i + 1;
            }
        }
        if (from < html.length()) {
            parts.add(html.substring(from));
        }
        if (parts.size() <= 1) {
            book.chapters.add(ch);
            return;
        }
        String baseTitle = ch.title == null ? "" : ch.title;
        for (int p = 0; p < parts.size(); p++) {
            String partHtml = parts.get(p);
            String partText = HtmlUtil.collapseWhitespace(HtmlUtil.toPlainText(partHtml));
            if (partText.length() == 0) {
                continue;
            }
            Chapter part = new Chapter(
                    p == 0 ? baseTitle : baseTitle + " (часть " + (p + 1) + ")", partText);
            part.html = partHtml;
            book.chapters.add(part);
        }
    }

    private static String cleanXml(String xml) {
        StringBuilder sb = new StringBuilder(xml.length());
        int len = xml.length();
        int i = 0;
        while (i < len) {
            char c = xml.charAt(i);
            if (c == '&') {
                int semi = xml.indexOf(';', i);
                if (semi > i && semi - i <= 14) {
                    String name = xml.substring(i + 1, semi).toLowerCase(Locale.US);
                    String replacement = GREEK.get(name);
                    if (replacement != null) {
                        sb.append(replacement);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            boolean keep;
            if (c >= 0x20 && c <= 0xD7FF) {
                keep = true;
            } else if (c == 0x09 || c == 0x0A || c == 0x0D) {
                keep = true;
            } else if (c >= 0xE000 && c <= 0xFFFD) {
                keep = true;
            } else if (Character.isHighSurrogate(c) && i + 1 < len
                    && Character.isLowSurrogate(xml.charAt(i + 1))) {
                keep = true;
            } else if (Character.isLowSurrogate(c)) {
                keep = false;
            } else {
                keep = false;
            }
            if (keep) {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }
}
