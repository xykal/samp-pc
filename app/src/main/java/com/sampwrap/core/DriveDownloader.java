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
        String id = fileId(urlOrId);
        if (id == null) id = urlOrId != null && urlOrId.matches("[A-Za-z0-9_-]{10,}")
                ? urlOrId : null;
        if (id == null) throw new IOException("Link Drive tidak dikenali: " + urlOrId);

        String base = "https://drive.usercontent.google.com/download";
        Map<String, String> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("export", "download");

        HttpURLConnection c = get(base + "?" + encode(q));
        String type = String.valueOf(c.getContentType());
        if (!type.contains("html")) return c;           // sudah file asli

        String formAction = base;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", id);
        form.put("export", "download");
        form.put("confirm", "t");

        Matcher ma = FORM_ACTION.matcher(readLimited(c));
        if (ma.find()) {
            formAction = ma.group(1).replace("&amp;", "&");
        }
        c.disconnect();

        // form kedua: baca input tersembunyi (uuid dsb.)
        HttpURLConnection c2 = get(formAction + "?" + encode(form));
        if (String.valueOf(c2.getContentType()).contains("html")) {
            String html = readLimited(c2);
            Matcher mi = FORM_INPUT.matcher(html);
            while (mi.find()) form.put(mi.group(1), mi.group(2).replace("&amp;", "&"));
            c2.disconnect();
            HttpURLConnection c3 = get(formAction + "?" + encode(form));
            if (String.valueOf(c3.getContentType()).contains("html")) {
                c3.disconnect();
                throw new IOException("Drive menolak unduhan (kuota / izin). "
                        + "Set link jadi 'Anyone with the link' lalu coba lagi.");
            }
            return c3;
        }
        return c2;
    }

    private static HttpURLConnection get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SAMP-PC");
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            throw new IOException("HTTP " + code + " dari Drive");
        }
        return c;
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
