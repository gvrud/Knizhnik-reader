package com.example.reader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MobiParser implements BookParser {

    @Override
    public Book parse(File file) throws IOException {
        Book book = new Book();
        book.filePath = file.getAbsolutePath();
        book.title = file.getName();
        book.author = "";

        byte[] all = readAll(file);
        if (all.length < 96) {
            throw new IOException("Файл слишком мал для MOBI");
        }

        int numRecords = u16(all, 76);
        if (numRecords < 1 || 78 + numRecords * 8 > all.length) {
            throw new IOException("Повреждённый PDB-заголовок");
        }

        int[] offsets = new int[numRecords + 1];
        for (int i = 0; i < numRecords; i++) {
            offsets[i] = (int) u32(all, 78 + i * 8);
            if (offsets[i] < 0 || offsets[i] >= all.length) {
                offsets[i] = all.length;
            }
        }
        offsets[numRecords] = all.length;

        if (offsets[0] + 16 > all.length) {
            throw new IOException("Нет PalmDOC-заголовка");
        }

        int compression = u16(all, offsets[0]);
        int textLength = (int) u32(all, offsets[0] + 4);

        String charset = null;
        String fullName = null;
        int firstContentRecord = 1;
        int firstImageIndex = 0;
        boolean multibyte = false;

        if (offsets[0] + 24 <= all.length && readTag(all, offsets[0] + 16).equals("MOBI")) {
            int mobi = offsets[0] + 16;
            long headerLength = u32(all, mobi + 20);
            if (mobi + 32 <= all.length) {
                int enc = (int) u32(all, mobi + 28);
                if (enc == 65001) {
                    charset = "UTF-8";
                } else if (enc == 1252 || enc == 1251 || enc == 1250) {
                    charset = "CP" + enc;
                }
            }
            if (mobi + 48 <= all.length) {
                int nameOff = (int) u32(all, mobi + 40);
                int nameLen = (int) u32(all, mobi + 44);
                if (nameLen > 0 && offsets[0] + nameOff + nameLen <= all.length) {
                    fullName = decodeWith(charset == null ? "CP1252" : charset,
                            all, offsets[0] + nameOff, nameLen);
                }
            }
            if (headerLength >= 116 && mobi + 112 <= all.length) {
                firstContentRecord = (int) u32(all, mobi + 108);
                if (firstContentRecord < 1) {
                    firstContentRecord = 1;
                }
                int firstImage = (int) u32(all, mobi + 64);
                if (firstImage > firstContentRecord) {
                    firstImageIndex = firstImage;
                }
            }
            if (headerLength >= 244 && mobi + 246 <= all.length) {
                int extraFlags = (int) u32(all, mobi + 242);
                multibyte = (extraFlags & 0x0002) != 0;
            }
            int exthPos = mobi + (int) headerLength;
            if (exthPos + 12 <= offsets[1] && readTag(all, exthPos).equals("EXTH")) {
                long exthLen = u32(all, exthPos + 4);
                int cnt = (int) u32(all, exthPos + 8);
                int pos = exthPos + 12;
                for (int i = 0; i < cnt && pos + 8 <= offsets[1] && pos < exthPos + exthLen; i++) {
                    int type = (int) u32(all, pos);
                    int len = (int) u32(all, pos + 4);
                    pos += 8;
                    if (len < 8 || pos + len - 8 > offsets[1]) {
                        break;
                    }
                    String val = decodeWith(charset == null ? "CP1252" : charset, all, pos, len - 8).trim();
                    if (type == 100 && val.length() > 0) {
                        book.author = val;
                    } else if (type == 503 && val.length() > 0) {
                        book.title = val;
                    }
                    pos += len - 8;
                }
            }
        }

        if (compression == 17480) {
            throw new IOException("Этот MOBI использует сжатие HUFF/CDIC, которое не поддерживается");
        }

        if (fullName != null && fullName.trim().length() > 0) {
            book.title = fullName.trim();
        }

        int lastRecord = firstImageIndex > firstContentRecord ? firstImageIndex : numRecords;
        if (lastRecord > numRecords) {
            lastRecord = numRecords;
        }

        ByteArrayOutputStream text = new ByteArrayOutputStream(Math.max(4096, textLength + 1024));
        for (int rec = firstContentRecord; rec < lastRecord; rec++) {
            int start = offsets[rec];
            int end = offsets[rec + 1];
            if (end <= start || start < 0 || end > all.length) {
                continue;
            }
            int len = end - start;
            int pos = 0;
            if (multibyte) {
                if (len < 2) {
                    continue;
                }
                int recLen = u16(all, start);
                pos = 2;
                if (pos + recLen <= len) {
                    len = pos + recLen;
                }
            }
            if (compression == 2) {
                decompressPalmDoc(all, start + pos, len - pos, text);
            } else {
                text.write(all, start + pos, len - pos);
            }
        }

        byte[] bytes = text.toByteArray();
        String decoded = charset != null
                ? decodeWith(charset, bytes, 0, bytes.length)
                : pickCyrillicEncoding(bytes);
        decoded = decoded.replace('\u0000', ' ');
        decoded = HtmlUtil.toPlainText(decoded);
        decoded = HtmlUtil.collapseWhitespace(decoded);

        if (decoded.trim().length() == 0) {
            throw new IOException("Не удалось извлечь текст из MOBI");
        }

        splitIntoChapters(book, decoded);
        return book;
    }

    private static void splitIntoChapters(Book book, String text) {
        final int chunk = 200000;
        if (text.length() <= chunk) {
            book.chapters.add(new Chapter("Книга", text));
            return;
        }
        int start = 0;
        int n = 1;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunk);
            if (end < text.length()) {
                int nl = text.lastIndexOf("\n", end);
                if (nl > start + chunk / 2) {
                    end = nl;
                }
            }
            book.chapters.add(new Chapter("Часть " + n, text.substring(start, end)));
            start = end;
            n++;
        }
    }

    private static String pickCyrillicEncoding(byte[] bytes) {
        String c1251 = decodeWith("CP1251", bytes, 0, bytes.length);
        int cyr = 0;
        int limit = Math.min(c1251.length(), 8000);
        for (int i = 0; i < limit; i++) {
            char ch = c1251.charAt(i);
            if ((ch >= 'А' && ch <= 'я') || ch == 'Ё' || ch == 'ё') {
                cyr++;
            }
        }
        if (cyr > 10) {
            return c1251;
        }
        return decodeWith("CP1252", bytes, 0, bytes.length);
    }

    private static String decodeWith(String charset, byte[] data, int off, int len) {
        try {
            return new String(data, off, len, charset);
        } catch (Exception e) {
            return new String(data, off, len);
        }
    }

    private static String readTag(byte[] a, int off) {
        if (off + 4 > a.length) {
            return "";
        }
        try {
            return new String(a, off, 4, "US-ASCII");
        } catch (Exception e) {
            return "";
        }
    }

    private static void decompressPalmDoc(byte[] src, int off, int len, ByteArrayOutputStream out) {
        byte[] buf = new byte[65536];
        int size = 0;
        int pos = off;
        int end = off + len;
        while (pos < end) {
            int c = src[pos++] & 0xFF;
            if (c == 0) {
                buf = ensure(buf, size);
                buf[size++] = 0;
                out.write(0);
            } else if (c <= 8) {
                for (int i = 0; i < c && pos < end; i++) {
                    byte b = src[pos++];
                    buf = ensure(buf, size);
                    buf[size++] = b;
                    out.write(b);
                }
            } else if (c < 0x80) {
                buf = ensure(buf, size);
                buf[size++] = (byte) c;
                out.write(c);
            } else if (c < 0xC0) {
                if (pos >= end) {
                    break;
                }
                int c2 = src[pos++] & 0xFF;
                int pair = (c << 8) | c2;
                int dist = (pair >> 3) & 0x7FF;
                int l = (pair & 0x07) + 3;
                for (int i = 0; i < l; i++) {
                    int srcIdx = size - dist;
                    if (srcIdx < 0) {
                        break;
                    }
                    byte b = buf[srcIdx];
                    buf = ensure(buf, size);
                    buf[size++] = b;
                    out.write(b);
                }
            } else {
                out.write(' ');
                buf = ensure(buf, size);
                buf[size++] = ' ';
                byte b = (byte) (c ^ 0x80);
                buf = ensure(buf, size);
                buf[size++] = b;
                out.write(b);
            }
        }
    }

    private static byte[] ensure(byte[] buf, int size) {
        if (size >= buf.length) {
            byte[] nb = new byte[buf.length * 2];
            System.arraycopy(buf, 0, nb, 0, buf.length);
            return nb;
        }
        return buf;
    }

    private static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            long flen = f.length();
            int initSize = (int) Math.min(flen >= 0 ? flen : 0, 50 * 1024 * 1024);
            if (initSize < 32768) {
                initSize = 32768;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(initSize);
            byte[] b = new byte[32768];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static int u16(byte[] a, int off) {
        return ((a[off] & 0xFF) << 8) | (a[off + 1] & 0xFF);
    }

    private static long u32(byte[] a, int off) {
        return ((long) (a[off] & 0xFF) << 24)
                | ((long) (a[off + 1] & 0xFF) << 16)
                | ((long) (a[off + 2] & 0xFF) << 8)
                | (a[off + 3] & 0xFF);
    }
}
