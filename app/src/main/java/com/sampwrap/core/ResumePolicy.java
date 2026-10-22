package com.sampwrap.core;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Aturan "boleh resume atau harus mulai dari nol" untuk unduhan HTTP.
 *
 * Kenapa dipisah: kasus HP putus-nyambung itu rumit. Server bisa membalas
 *   200 (Range diabaikan)  -> wajib tulis dari awal, kalau tidak file jadi dobel
 *   206 (partial content)  -> lanjutkan, tapi cek dulu awal range-nya cocok
 *   416 (range not satisfiable) -> TIDAK berarti "sudah lengkap"!
 *       416 cuma berarti offset yang kita minta di luar ukuran file. Penyebab
 *       nyata di lapangan: file sementara (.rar) lebih besar dari file aslinya
 *       karena sesi lama menulis halaman HTML/isi file lain ke situ, atau link
 *       Drive-nya sudah beda file. Kalau 416 dianggap "selesai", ekstraksi
 *       langsung gagal ("Tidak menemukan RAR di dalam paket").
 *
 * Satu-satunya cara 416 boleh dianggap selesai: header
 * `Content-Range: bytes *\/<total>` ada dan ukuran file lokal == total itu.
 */
public final class ResumePolicy {

    public enum Action {
        /** tulis dari byte 0 (truncate) */
        RESTART,
        /** lanjutkan dari ukuran file lokal */
        APPEND,
        /** tidak perlu unduh lagi */
        COMPLETE
    }

    private ResumePolicy() {}

    /**
     * Kode status HTTP, tahan terhadap JVM yang melempar IOException untuk
     * balasan 4xx (OpenJDK: "Server returned HTTP response code: 416 ...").
     * Android mengembalikan kodenya seperti biasa — dua-duanya harus jalan.
     */
    public static int status(HttpURLConnection c) throws IOException {
        try {
            return c.getResponseCode();
        } catch (IOException e) {
            int code = codeFromMessage(e.getMessage());
            if (code > 0) return code;
            throw e;
        }
    }

    /** "…HTTP response code: 416 for URL…" -> 416, selain itu -1. */
    public static int codeFromMessage(String message) {
        if (message == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("HTTP response code:?\\s*(\\d{3})").matcher(message);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Total ukuran file dari header Content-Range.
     * Contoh: "bytes 100-199/200" -> 200, "bytes *\/200" -> 200. -1 kalau tidak ada.
     */
    public static long totalFromContentRange(String header) {
        if (header == null) return -1;
        int slash = header.lastIndexOf('/');
        if (slash < 0 || slash == header.length() - 1) return -1;
        String t = header.substring(slash + 1).trim();
        if (t.equals("*")) return -1;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Awal range dari header Content-Range ("bytes 100-199/200" -> 100).
     * -1 kalau tidak ada / bentuknya "bytes *\/200".
     */
    public static long startFromContentRange(String header) {
        if (header == null) return -1;
        int sp = header.indexOf(' ');
        if (sp < 0) return -1;
        int dash = header.indexOf('-', sp + 1);
        if (dash < 0) return -1;
        String s = header.substring(sp + 1, dash).trim();
        if (s.equals("*")) return -1;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Putuskan apa yang harus dilakukan terhadap file lokal.
     *
     * @param localBytes    ukuran file sementara yang sudah ada (0 kalau belum ada)
     * @param code          kode HTTP (200/206/416)
     * @param contentRange  nilai header Content-Range (boleh null)
     * @param contentLength Content-Length balasan (boleh -1)
     */
    public static Action decide(long localBytes, int code, String contentRange,
                                long contentLength) {
        if (code == 416) {
            long total = totalFromContentRange(contentRange);
            if (total > 0 && localBytes == total) return Action.COMPLETE;
            // termasuk kasus total tidak diketahui: jangan tebak "selesai"
            return Action.RESTART;
        }
        if (code == 206) {
            long start = startFromContentRange(contentRange);
            if (localBytes > 0 && start >= 0 && start != localBytes) return Action.RESTART;
            if (localBytes == 0) return Action.RESTART;
            return Action.APPEND;
        }
        if (code == 200) {
            // server mengabaikan Range -> mulai dari nol, kalau tidak jadi dobel
            return Action.RESTART;
        }
        return Action.RESTART;
    }

    /**
     * Ukuran total yang bisa dipercaya setelah balasan ini, atau -1.
     * 206: start + Content-Length. 200: Content-Length. 416: dari Content-Range.
     */
    public static long totalAfter(long localBytes, int code, String contentRange,
                                  long contentLength) {
        if (code == 416) return totalFromContentRange(contentRange);
        if (code == 206) {
            long start = startFromContentRange(contentRange);
            if (contentLength >= 0) {
                return (start >= 0 ? start : localBytes) + contentLength;
            }
            return totalFromContentRange(contentRange);
        }
        if (code == 200) return contentLength > 0 ? contentLength : -1;
        return -1;
    }
}
