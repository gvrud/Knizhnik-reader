package com.example.reader;

import android.util.Base64;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fb2Parser implements BookParser {

    private static final Pattern ENTITY_REF = Pattern.compile("&([a-zA-Z]+);");
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

        byte[] raw = readAll(file);
        String xml = decode(raw);
        xml = replaceGreekEntities(xml);

        try {
            parseXml(book, xml, file.getName());
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка разбора FB2: " + e.getMessage());
        }
        return book;
    }

    private void parseXml(Book book, String xml, String fileName) throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xml));

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

    private static String decode(byte[] raw) {
        String charset = detectCharset(raw);
        int off = 0;
        if ("UTF-8".equals(charset) && raw.length >= 3
                && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
            off = 3;
        } else if (("UTF-16LE".equals(charset) || "UTF-16BE".equals(charset)) && raw.length >= 2) {
            off = 2;
        }
        try {
            return new String(raw, off, raw.length - off, charset);
        } catch (Exception e) {
            try {
                return new String(raw, off, raw.length - off, "UTF-8");
            } catch (Exception e2) {
                return new String(raw, off, raw.length - off);
            }
        }
    }

    private static String detectCharset(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) {
            return "UTF-16BE";
        }
        int n = Math.min(raw.length, 512);
        String head;
        try {
            head = new String(raw, 0, n, "ISO-8859-1");
        } catch (Exception e) {
            return "UTF-8";
        }
        Matcher m = ENCODING_PATTERN.matcher(head);
        if (m.find()) {
            return mapCharset(m.group(1).trim().toLowerCase(Locale.US));
        }
        return "UTF-8";
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
            book.chapters.add(ch);
        }
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(f.length(), 1024 * 1024));
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String replaceGreekEntities(String xml) {
        Matcher m = ENTITY_REF.matcher(xml);
        StringBuilder sb = new StringBuilder(xml.length());
        int pos = 0;
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.US);
            String replacement = GREEK.get(name);
            if (replacement != null) {
                sb.append(xml, pos, m.start());
                sb.append(replacement);
                pos = m.end();
            } else {
                pos = m.end();
            }
        }
        sb.append(xml, pos, xml.length());
        return sb.toString();
    }
}
