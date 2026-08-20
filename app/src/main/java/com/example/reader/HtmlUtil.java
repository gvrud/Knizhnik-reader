package com.example.reader;

public final class HtmlUtil {

    private HtmlUtil() {
    }

    private static final String[][] GREEK_MAP = new String[][] {
        {"alpha","\u03B1"},{"beta","\u03B2"},{"gamma","\u03B3"},{"delta","\u03B4"},
        {"epsilon","\u03B5"},{"zeta","\u03B6"},{"eta","\u03B7"},{"theta","\u03B8"},
        {"iota","\u03B9"},{"kappa","\u03BA"},{"lambda","\u03BB"},{"mu","\u03BC"},
        {"nu","\u03BD"},{"xi","\u03BE"},{"omicron","\u03BF"},{"pi","\u03C0"},
        {"rho","\u03C1"},{"sigmaf","\u03C2"},{"sigma","\u03C3"},{"tau","\u03C4"},
        {"upsilon","\u03C5"},{"phi","\u03C6"},{"chi","\u03C7"},{"psi","\u03C8"},
        {"omega","\u03C9"},
        {"Alpha","\u0391"},{"Beta","\u0392"},{"Gamma","\u0393"},{"Delta","\u0394"},
        {"Epsilon","\u0395"},{"Zeta","\u0396"},{"Eta","\u0397"},{"Theta","\u0398"},
        {"Iota","\u0399"},{"Kappa","\u039A"},{"Lambda","\u039B"},{"Mu","\u039C"},
        {"Nu","\u039D"},{"Xi","\u039E"},{"Omicron","\u039F"},{"Pi","\u03A0"},
        {"Rho","\u03A1"},{"Sigma","\u03A3"},{"Tau","\u03A4"},{"Upsilon","\u03A5"},
        {"Phi","\u03A6"},{"Chi","\u03A7"},{"Psi","\u03A8"},{"Omega","\u03A9"},
        {"thetasym","\u03D1"},{"upsih","\u03D2"},{"piv","\u03D6"},
    };

    private static boolean isGreekEntity(String name) {
        for (String[] pair : GREEK_MAP) {
            if (pair[0].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String greekToUnicode(String name) {
        for (String[] pair : GREEK_MAP) {
            if (pair[0].equalsIgnoreCase(name)) {
                return pair[1];
            }
        }
        return null;
    }

    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(html.length());
        int len = html.length();
        int i = 0;
        while (i < len) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i);
                if (end < 0) {
                    break;
                }
                String tag = html.substring(i + 1, end).trim().toLowerCase();
                String name = tag;
                int sp = tag.indexOf(' ');
                if (sp > 0) {
                    name = tag.substring(0, sp);
                }
                while (name.length() > 0 && (name.charAt(0) == '/' || name.charAt(name.length() - 1) == '/')) {
                    if (name.charAt(0) == '/') {
                        name = name.substring(1);
                    }
                    if (name.length() > 0 && name.charAt(name.length() - 1) == '/') {
                        name = name.substring(0, name.length() - 1);
                    }
                }
                if ("p".equals(name) || "br".equals(name) || "div".equals(name)
                        || "li".equals(name) || "h1".equals(name) || "h2".equals(name)
                        || "h3".equals(name) || "h4".equals(name) || "h5".equals(name)
                        || "h6".equals(name) || "tr".equals(name) || "hr".equals(name)
                        || "blockquote".equals(name)) {
                    out.append('\n');
                }
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return collapseWhitespace(unescape(out.toString()));
    }

    public static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 12) {
                    String ent = s.substring(i + 1, semi);
                    String rep = null;
                    if ("amp".equals(ent)) {
                        rep = "&";
                    } else if ("lt".equals(ent)) {
                        rep = "<";
                    } else if ("gt".equals(ent)) {
                        rep = ">";
                    } else if ("quot".equals(ent)) {
                        rep = "\"";
                    } else if ("apos".equals(ent)) {
                        rep = "'";
                    } else if ("nbsp".equals(ent)) {
                        rep = " ";
                    } else if ("mdash".equals(ent)) {
                        rep = "\u2014";
                    } else if ("ndash".equals(ent)) {
                        rep = "\u2013";
                    } else if ("hellip".equals(ent)) {
                        rep = "\u2026";
                    } else if ("laquo".equals(ent)) {
                        rep = "\u00AB";
                    } else if ("raquo".equals(ent)) {
                        rep = "\u00BB";
                    } else if ("ldquo".equals(ent)) {
                        rep = "\u201C";
                    } else if ("rdquo".equals(ent)) {
                        rep = "\u201D";
                    } else if ("lsquo".equals(ent)) {
                        rep = "\u2018";
                    } else if ("rsquo".equals(ent)) {
                        rep = "\u2019";
                    } else if ("rarr".equals(ent)) {
                        rep = "\u2192";
                    } else if ("larr".equals(ent)) {
                        rep = "\u2190";
                    } else if ("deg".equals(ent)) {
                        rep = "\u00B0";
                    } else if ("copy".equals(ent)) {
                        rep = "\u00A9";
                    } else if ("reg".equals(ent)) {
                        rep = "\u00AE";
                    } else if ("trade".equals(ent)) {
                        rep = "\u2122";
                    } else if ("bull".equals(ent)) {
                        rep = "\u2022";
                    } else if ("middot".equals(ent)) {
                        rep = "\u00B7";
                    } else if ("sect".equals(ent)) {
                        rep = "\u00A7";
                    } else if ("para".equals(ent)) {
                        rep = "\u00B6";
                    } else if ("plusmn".equals(ent)) {
                        rep = "\u00B1";
                    } else if ("times".equals(ent)) {
                        rep = "\u00D7";
                    } else if ("divide".equals(ent)) {
                        rep = "\u00F7";
                    } else if ("shy".equals(ent)) {
                        rep = "";
                    } else if (isGreekEntity(ent)) {
                        rep = greekToUnicode(ent);
                    } else if (ent.length() > 1 && ent.charAt(0) == '#') {
                        try {
                            int code;
                            if (ent.charAt(1) == 'x' || ent.charAt(1) == 'X') {
                                code = Integer.parseInt(ent.substring(2), 16);
                            } else {
                                code = Integer.parseInt(ent.substring(1));
                            }
                            rep = new String(Character.toChars(code));
                        } catch (Exception e) {
                            rep = null;
                        }
                    }
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
                sb.append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String collapseWhitespace(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        boolean afterNewline = true;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\r') {
                i++;
                continue;
            }
            if (c == '\n') {
                if (!afterNewline) {
                    sb.append('\n');
                    afterNewline = true;
                }
                i++;
                continue;
            }
            if (c == ' ' || c == '\t') {
                char prev = sb.length() > 0 ? sb.charAt(sb.length() - 1) : 0;
                if (prev != ' ' && prev != '\n' && prev != 0) {
                    sb.append(' ');
                }
                i++;
                continue;
            }
            sb.append(c);
            afterNewline = false;
            i++;
        }
        return sb.toString();
    }
}