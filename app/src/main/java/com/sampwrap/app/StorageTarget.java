package com.sampwrap.app;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pilih lokasi folder game: internal app, storage publik, atau SD card.
 *
 * - INTERNAL : /storage/emulated/0/Android/data/<pkg>/files/Games/GTA-SA
 *              selalu bisa ditulis tanpa permission.
 * - PUBLIC   : /storage/emulated/0/Games/GTA-SA
 *              butuh "Akses semua file" (MANAGE_EXTERNAL_STORAGE) di Android 11+.
 * - SDCARD   : /storage/XXXX-XXXX/Games/GTA-SA
 *              juga butuh "Akses semua file". Terdeteksi otomatis.
 *
 * Catatan performa: SD card bikin texture streaming lambat (stutter di kota).
 * Internal selalu lebih cepat — app menampilkan peringatan kalau user pilih SD.
 */
public final class StorageTarget {

    public enum Mode { INTERNAL, PUBLIC, SDCARD }

    public static final String GAME_SUBDIR = "Games/GTA-SA";

    private StorageTarget() {}

    // ------------------------------------------------------------------ path --

    public static File dir(Context ctx, Mode mode) {
        switch (mode) {
            case PUBLIC:
                return new File(Environment.getExternalStorageDirectory(), GAME_SUBDIR);
            case SDCARD:
                File sd = sdCardRoot();
                if (sd != null) return new File(sd, GAME_SUBDIR);
                return new File(Environment.getExternalStorageDirectory(), GAME_SUBDIR);
            case INTERNAL:
            default:
                File root = ctx.getExternalFilesDir(null);
                if (root == null) root = ctx.getFilesDir();
                return new File(root, GAME_SUBDIR);
        }
    }

    public static Mode currentMode(Context ctx) {
        String v = ctx.getSharedPreferences("sampwrap", Context.MODE_PRIVATE)
                .getString("storage_mode", Mode.INTERNAL.name());
        try { return Mode.valueOf(v); } catch (Exception e) { return Mode.INTERNAL; }
    }

    public static void setMode(Context ctx, Mode m) {
        ctx.getSharedPreferences("sampwrap", Context.MODE_PRIVATE)
                .edit().putString("storage_mode", m.name()).apply();
    }

    /** Root SD card yang bisa ditulisi, atau null. */
    public static File sdCardRoot() {
        File[] dirs = null;
        if (Build.VERSION.SDK_INT >= 19) {
            dirs = new File[]{new File("/storage")};
        }
        File best = null;
        File storage = new File("/storage");
        File[] kids = storage.listFiles();
        if (kids != null) {
            for (File k : kids) {
                String n = k.getName();
                if (n.equals("emulated") || n.equals("self")) continue;
                if (k.isDirectory() && k.canWrite()) { best = k; break; }
            }
        }
        return best;
    }

    public static List<String> describeAll(Context ctx) {
        List<String> out = new ArrayList<>();
        out.add("Internal aplikasi (tercepat, tanpa izin)\n" + dir(ctx, Mode.INTERNAL));
        out.add("Storage publik\n" + dir(ctx, Mode.PUBLIC)
                + (canWritePublic() ? "" : "\nbutuh izin \"Akses semua file\""));
        File sd = sdCardRoot();
        out.add(sd == null
                ? "SD card\n(tidak terdeteksi)"
                : "SD card\n" + dir(ctx, Mode.SDCARD) + "\nlebih lambat saat streaming tekstur");
        return out;
    }

    public static boolean canWritePublic() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return true;
    }

    // ---------------------------------------------------------------- ruang ---

    public static long freeBytes(File dir) {
        try {
            File probe = dir;
            while (probe != null && !probe.exists()) probe = probe.getParentFile();
            if (probe == null) return -1;
            StatFs st = new StatFs(probe.getAbsolutePath());
            return st.getAvailableBlocksLong() * st.getBlockSizeLong();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Cek apakah ruang cukup untuk paket + ruang ekstra (ekstrak/patch).
     * @return pesan siap tampil, atau null kalau aman.
     */
    public static String checkSpace(File dir, long neededBytes) {
        long free = freeBytes(dir);
        if (free < 0) return null;
        long needed = neededBytes + (128L * 1024 * 1024); // 128 MB untuk cache/temp
        if (free < needed) {
            return "Ruang tidak cukup.\n\n"
                    + "Butuh : " + com.sampwrap.core.Manifest.humanSize(needed) + "\n"
                    + "Tersedia: " + com.sampwrap.core.Manifest.humanSize(free) + "\n\n"
                    + "Pilih paket lebih kecil atau kosongkan storage.";
        }
        return null;
    }
}
