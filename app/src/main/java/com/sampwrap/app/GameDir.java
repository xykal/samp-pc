package com.sampwrap.app;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Lokasi folder game.
 *
 * Default: /storage/emulated/0/Android/data/com.sampwrap.app/files/Games/GTA-SA
 * -> bisa dibaca app ini sendiri tanpa permission, dan bisa diakses user lewat
 *    file manager (Android 11+ membatasi folder lain, jadi ini paling aman).
 *
 * Kalau app digabung ke fork Winlator (see README), path ini tinggal di-mount
 * jadi drive D: container.
 */
public final class GameDir {

    private GameDir() {}

    public static File get(Context ctx) {
        File g = StorageTarget.dir(ctx, StorageTarget.currentMode(ctx));
        if (!g.exists()) g.mkdirs();
        return g;
    }

    /** Info yang ditampilkan di UI. */
    public static String describe(Context ctx) {
        File g = get(ctx);
        return g.getAbsolutePath();
    }

    /** Ukuran folder secara rekursif. */
    public static long size(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long total = 0;
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) total += size(k);
        return total;
    }

    public static boolean isExternal(File f) {
        return f != null && f.getAbsolutePath().startsWith(
                Environment.getExternalStorageDirectory().getAbsolutePath());
    }
}
