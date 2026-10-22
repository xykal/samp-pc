package com.sampwrap.core;

import java.net.HttpURLConnection;

/**
 * Header yang wajib sama untuk SEMUA unduhan.
 *
 * Kenapa ada kelas ini: GitHub Releases membalas 406 kalau header Accept
 * bawaan JVM/Android dikirim apa adanya ("text/html, image/gif, ... ,
 * star-slash-star ; q=.2"). Bukan salah Range, bukan salah link. Diukur:
 * Accept bawaan itu = 406, Accept bintang = 200.
 *
 * Accept-Encoding dibuat identity supaya Content-Length sama dengan ukuran
 * file (kalau di-gzip, progres unduhan jadi tidak cocok dengan byte).
 */
public final class HttpTune {

    private HttpTune() {}

    public static void apply(HttpURLConnection c) {
        if (c == null) return;
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) SAMP-PC");
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setInstanceFollowRedirects(true);
    }
}
