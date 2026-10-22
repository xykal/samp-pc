package com.sampwrap.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Unduhan besar yang tahan putus-nyambung: retry + HTTP Range + pemulihan 416.
 *
 * Murni Java (tanpa Android) supaya bisa diuji di PC. Pemanggil hanya perlu
 * memberi "cara membuka koneksi untuk offset tertentu".
 */
public final class ResumableDownload {

    /** Cara membuka koneksi, mulai dari byte ke-{start} (0 = dari awal). */
    public interface Source {
        HttpURLConnection open(long start) throws IOException;
    }

    public interface Listener {
        void onLog(String message);
        /** done/total dalam byte; total -1 kalau server tidak memberi ukuran. */
        void onProgress(long done, long total);
    }

    private static final int BUFFER = 128 * 1024;

    private ResumableDownload() {}

    /**
     * Unduh {@code dst} sampai selesai.
     *
     * @return jumlah byte file setelah selesai
     */
    public static long fetch(File dst, Source source, int maxAttempts, Listener listener)
            throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return attemptOnce(dst, source, listener, 2);
            } catch (Exception e) {
                last = e;
                log(listener, "gagal: " + msg(e));
                if (attempt < maxAttempts) {
                    int wait = Math.min(15, attempt * 2);
                    log(listener, "coba lagi " + attempt + "/" + (maxAttempts - 1)
                            + " dalam " + wait + " dtk…");
                    try {
                        Thread.sleep(wait * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Unduhan dibatalkan");
                    }
                }
            }
        }
        throw new Exception("Unduhan gagal setelah " + maxAttempts + " percobaan. Terakhir: "
                + msg(last));
    }

    /**
     * @param restartsLeft berapa kali boleh "buang file sementara lalu mulai dari 0"
     *                     dalam satu percobaan (416 / Range tidak cocok).
     */
    private static long attemptOnce(File dst, Source source, Listener listener, int restartsLeft)
            throws Exception {

        // Sisa sesi lama yang ternyata halaman HTML (kuota Drive habis, dsb.)
        // JANGAN di-resume: itu bukan potongan file.
        if (dst.isFile() && looksLikeHtml(dst)) {
            log(listener, "file sementara berisi halaman HTML → dibuang");
            if (!dst.delete()) throw new IOException("tidak bisa menghapus " + dst);
        }

        long have = dst.isFile() ? dst.length() : 0;
        HttpURLConnection c = null;

        try {
            log(listener, "buka mulai byte " + have);
            c = source.open(have);
            int code = c.getResponseCode();
            String type = String.valueOf(c.getContentType());
            long len = c.getContentLengthLong();
            String cr = c.getHeaderField("Content-Range");
            log(listener, "HTTP " + code + " tipe=" + type + " panjang=" + len
                    + (cr == null ? "" : " range=" + cr));

            if (code == 416) {
                long total = ResumePolicy.totalFromContentRange(cr);
                if (total > 0 && have == total) {
                    log(listener, "416 tapi ukuran lokal == total (" + total + ") → selesai");
                    return have;
                }
                // 416 BUKAN "sudah lengkap". File sementara lebih besar dari
                // file di server (isi file lain / HTML / link beda).
                log(listener, "416: ukuran lokal (" + have + ") tidak cocok dengan file di server"
                        + (total > 0 ? " (" + total + ")" : " (total tidak diketahui)")
                        + " → mulai dari nol");
                disconnect(c);
                c = null;
                if (restartsLeft <= 0) {
                    throw new IOException(have == 0
                            ? "Drive menolak unduhan (HTTP 416) padahal mulai dari awal — "
                              + "link mungkin sudah berubah atau file dihapus"
                            : "server menolak range berulang (HTTP 416)");
                }
                if (have > 0 && dst.isFile() && !dst.delete()) {
                    throw new IOException("tidak bisa menghapus " + dst);
                }
                return attemptOnce(dst, source, listener, restartsLeft - 1);
            }

            if (code != 200 && code != 206) {
                throw new IOException("server membalas HTTP " + code);
            }
            if (type.toLowerCase().contains("html")) {
                throw new IOException("server kirim halaman HTML, bukan file "
                        + "(biasanya kuota Drive habis atau link tidak publik)");
            }

            if (code == 206 && have > 0) {
                long start = ResumePolicy.startFromContentRange(cr);
                if (start >= 0 && start != have) {
                    log(listener, "206 mulai di byte " + start + " padahal lokal " + have
                            + " → mulai dari nol");
                    disconnect(c);
                    c = null;
                    if (restartsLeft <= 0) throw new IOException("range server tidak cocok terus");
                    if (dst.isFile() && !dst.delete()) throw new IOException("tidak bisa hapus " + dst);
                    return attemptOnce(dst, source, listener, restartsLeft - 1);
                }
            }

            ResumePolicy.Action action = ResumePolicy.decide(have, code, cr, len);
            if (action == ResumePolicy.Action.COMPLETE) return have;
            if (action == ResumePolicy.Action.RESTART) have = 0;

            long total = ResumePolicy.totalAfter(have, code, cr, len);
            boolean append = action == ResumePolicy.Action.APPEND && have > 0;
            if (!append) have = 0;

            long done = have;
            listener.onProgress(done, total);
            try (InputStream in = new BufferedInputStream(c.getInputStream(), BUFFER);
                 FileOutputStream fos = new FileOutputStream(dst, append)) {
                byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    done += n;
                    listener.onProgress(done, total);
                }
            }

            long got = dst.isFile() ? dst.length() : 0;
            if (total > 0 && got < total) {
                throw new IOException("koneksi terputus di " + mb(got) + "/" + mb(total));
            }
            log(listener, "selesai " + got + " byte");
            return got;
        } finally {
            disconnect(c);
        }
    }

    private static String mb(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private static void disconnect(HttpURLConnection c) {
        if (c != null) {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void log(Listener l, String s) {
        if (l != null) l.onLog(s);
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    /** Cek 512 byte pertama: apakah ini halaman HTML, bukan potongan file biner. */
    public static boolean looksLikeHtml(File f) {
        if (f == null || !f.isFile() || f.length() <= 0) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] head = new byte[(int) Math.min(512, f.length())];
            raf.readFully(head);
            String s = new String(head, StandardCharsets.ISO_8859_1).trim().toLowerCase();
            return s.startsWith("<!doctype html") || s.startsWith("<html")
                    || s.startsWith("<!doctype") && s.contains("html");
        } catch (IOException e) {
            return false;
        }
    }
}
