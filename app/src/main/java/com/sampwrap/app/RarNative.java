package com.sampwrap.app;

import android.util.Log;

/**
 * Jembatan ke libsampunrar (UnRAR milik RARLAB, dikompilasi via NDK).
 *
 * Kenapa perlu: paket dari Google Drive bentuknya RAR5 (dan di dalamnya ada
 * RAR lagi). Android tidak bisa membuka RAR, library Java (junrar) juga tidak
 * mendukung RAR5. Jadi kita bawa unrar sendiri di APK.
 *
 * Aman kalau engine belum ada: semua panggilan mengembalikan null, pemanggil
 * yang memutuskan fallback.
 */
public final class RarNative {

    public interface Progress {
        /** @param name nama entri (kosong kalau tidak diketahui) */
        void onData(String name, long doneBytes, long totalBytes);
    }

    private static volatile boolean loaded;
    private static volatile boolean failed;

    private RarNative() {}

    static synchronized boolean load() {
        if (loaded) return true;
        if (failed) return false;
        try {
            System.loadLibrary("sampunrar");
            loaded = true;
        } catch (Throwable t) {
            Log.w("RarNative", "libsampunrar tidak ada: " + t);
            failed = true;
        }
        return loaded;
    }

    static native long[] extractNative(String rarPath, String outDir,
                                       String[] wanted, Progress cb);

    /**
     * Ekstrak entri terpilih.
     *
     * @param wanted daftar path relatif (dicocokkan sebagai akhiran), atau
     *               pola ekstensi seperti "*.rar"
     * @return {jumlah file, total byte}, atau null kalau lib tidak tersedia
     */
    public static long[] extract(String rarPath, String outDir,
                                 String[] wanted, Progress cb) {
        if (!load()) return null;
        try {
            return extractNative(rarPath, outDir, wanted, cb);
        } catch (Throwable t) {
            Log.e("RarNative", "gagal ekstrak " + rarPath, t);
            return null;
        }
    }

    public static boolean available() {
        return load();
    }
}
