package com.sampwrap.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unduh file Google Drive "share link" tanpa SDK.
 *
 * Kenapa perlu: link Drive yang user tempel bentuknya
 *   https://drive.google.com/file/d/<ID>/view?usp=sharing
 * bukan URL file langsung. Kalau dibuka apa adanya, yang datang halaman HTML
 * "virus scan warning", bukan ZIP-nya.
 *
 * Alur di sini:
 *   1. ambil ID dari URL apa pun bentuknya,
 *   2. buka drive.usercontent.google.com dengan cookie jar,
 *   3. kalau yang balik HTML (file gede), baca <form> konfirmasi dan
 *      ulangi GET dengan parameter form (id/export/confirm/uuid),
 *   4. kalau tetap HTML (kuota habis / minta izin), lempar error yang jelas.
 */
public final class DriveDownloader {

    private static final Pattern ID_PATH =
            Pattern.compile("/file/d/([A-Za-z0-9_-]{10,})");
    private static final Pattern ID_QUERY =
            Pattern.compile("[?&]id=([A-Za-z0-9_-]{10,})");
    private static final Pattern FORM_ACTION =
            Pattern.compile("action=\"([^\"]+)\"");
    private static final Pattern FORM_INPUT =
            Pattern.compile("<input[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"");

    static {
        // Google memakai cookie pada alur halaman konfirmasi. Tanpa ini,
        // permintaan kedua bisa ditolak 403.
        try {
            java.net.CookieHandler.setDefault(
                    new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL));
        } catch (Throwable ignored) {}
    }

    private DriveDownloader() {}

    public static boolean isDrive(String url) {
        return url != null && (url.contains("drive.google.com")
                || url.contains("docs.google.com"));
    }

    /** Ambil file ID dari berbagai bentuk link Drive. Null kalau tidak ketemu. */
    public static String fileId(String url) {
        if (url == null) return null;
        Matcher m = ID_PATH.matcher(url);
        if (m.find()) return m.group(1);
        m = ID_QUERY.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    /** URL langsung (kalau user sudah punya ID-nya). */
    public static String directUrl(String id) {
        return "https://drive.usercontent.google.com/download?id="
                + id + "&export=download&confirm=t";
    }

    /**
     * Buka stream isi file. Pemanggil yang menutup stream-nya.
     * Content-Length bisa -1 (chunked) — jangan diasumsikan ada.
     */
    public static HttpURLConnection open(String urlOrId) throws IOException {
        return open(urlOrId, 0);
    }

    /**
     * Buka isi file, mulai dari byte ke-{start} (resume).
     * Kalau server mengabaikan Range, balasan tetap 200 dan pemanggil harus
     * mengulang dari nol (cek getResponseCode()).
     */
    public static HttpURLConnection open(String urlOrId, long start) throws IOException {
        String id = fileId(urlOrId);
        if (id == null) id = urlOrId != null && urlOrId.matches("[A-Za-z0-9_-]{10,}")
                ? urlOrId : null;
        if (id == null) throw new IOException("Link Drive tidak dikenali: " + urlOrId);

        IOException last = null;
        StringBuilder tried = new StringBuilder();
        String[] bases = {
                "https://drive.usercontent.google.com/download",
                "https://drive.google.com/uc",
                "https://docs.google.com/uc"
        };
        for (String base : bases) {
            try {
                HttpURLConnection c = attempt(base, id, start);
                return c;
            } catch (IOException e) {
                if (tried.length() > 0) tried.append(", ");
                tried.append(hostOf(base)).append("=").append(shortMsg(e));
                last = e;
            }
        }
        throw new IOException((last == null ? "Drive tidak bisa diakses" : last.getMessage())
                + "\n\n(endpoint dicoba: " + tried + ")");
    }

    private static String hostOf(String url) {
        int a = url.indexOf("//") + 2, b = url.indexOf('/', a);
        return b > a ? url.substring(a, b) : url.substring(a);
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    /** Satu percobaan ke satu endpoint: langsung file, atau lewat halaman konfirmasi. */
    private static HttpURLConnection attempt(String base, String id, long start) throws IOException {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("export", "download");

        HttpURLConnection c = get(base + "?" + encode(q), start);
        // 416 = range tidak masuk akal. JANGAN dianggap error di sini: yang
        // memutuskan "mulai dari nol" adalah ResumableDownload/ResumePolicy.
        if (c.getResponseCode() == 416) return c;
        if (!String.valueOf(c.getContentType()).contains("html")) return c;

        // halaman "virus scan warning" -> baca form konfirmasi + uuid
        String html = readLimited(c);
        c.disconnect();

        String action = base;
        Matcher ma = FORM_ACTION.matcher(html);
        if (ma.find()) action = ma.group(1).replace("&amp;", "&");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", id);
        form.put("export", "download");
        form.put("confirm", "t");
        Matcher mi = FORM_INPUT.matcher(html);
        while (mi.find()) form.put(mi.group(1), mi.group(2).replace("&amp;", "&"));

        HttpURLConnection c2 = get(action + "?" + encode(form), start);
        if (String.valueOf(c2.getContentType()).contains("html")) {
            String h2 = readLimited(c2);
            c2.disconnect();
            throw new IOException("Drive mengirim halaman, bukan file: " + summarize(h2));
        }
        return c2;
    }

    /** Ambil inti pesan dari halaman HTML Google (biar user tahu sebabnya). */
    private static String summarize(String html) {
        String t = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("&amp;", "&").replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
        if (t.length() > 220) t = t.substring(0, 220) + "…";
        return t.isEmpty() ? "(halaman kosong)" : t;
    }

    private static HttpURLConnection get(String url, long start) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SAMP-PC");
        if (start > 0) c.setRequestProperty("Range", "bytes=" + start + "-");
        int code = c.getResponseCode();
        // 416 dibiarkan lolos: header Content-Range-nya justru dipakai untuk
        // memutuskan apakah file lokal sudah lengkap atau harus diulang dari 0.
        if (code >= 400 && code != 416) {
            String body = snippet(c);
            c.disconnect();
            String hint = "";
            if (code == 403) {
                hint = " — biasanya kuota file Drive habis (file terlalu sering diunduh). "
                        + "Tunggu beberapa jam, ganti link paket, atau impor file RAR yang "
                        + "kamu unduh lewat browser.";
            } else if (code == 404) {
                hint = " — file tidak ditemukan / link sudah berubah.";
            } else if (code == 429) {
                hint = " — terlalu banyak permintaan, tunggu sebentar.";
            }
            throw new IOException("Drive membalas HTTP " + code + hint
                    + (body.isEmpty() ? "" : "\n(" + body + ")"));
        }
        return c;
    }

    /** Beberapa ratus byte pertama body, buat pesan error yang berguna. */
    private static String snippet(HttpURLConnection c) {
        try {
            java.io.InputStream in = c.getErrorStream();
            if (in == null) return "";
            byte[] b = new byte[400];
            int n = in.read(b);
            in.close();
            if (n <= 0) return "";
            String s = new String(b, 0, n, "UTF-8")
                    .replaceAll("(?s)<[^>]*>", " ")
                    .replaceAll("\\s+", " ").trim();
            return s.length() > 200 ? s.substring(0, 200) : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Ringkasan singkat untuk layar Diagnostik (tanpa mengunduh isinya). */
    public static String probe(String urlOrId) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = open(urlOrId);
            int code = c.getResponseCode();
            String type = String.valueOf(c.getContentType());
            long len = c.getContentLengthLong();
            String out = "HTTP " + code
                    + "\ntipe: " + type
                    + "\nukuran: " + (len > 0 ? (len / (1024 * 1024)) + " MB (" + len + " B)" : "tidak diketahui")
                    + "\nwaktu: " + (System.currentTimeMillis() - t0) + " ms";
            return out;
        } catch (Throwable e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            return "GAGAL: " + m + "\nwaktu: " + (System.currentTimeMillis() - t0) + " ms";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readLimited(HttpURLConnection c) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = c.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 200_000) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String encode(Map<String, String> q) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }
}
