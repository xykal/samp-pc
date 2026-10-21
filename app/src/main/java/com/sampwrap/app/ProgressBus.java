package com.sampwrap.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Jembatan progress antar-proses.
 *
 * InstallService sengaja jalan di proses terpisah (":sync") supaya kalau
 * ekstraksi native (unrar) meledak, UI tetap hidup dan user cuma lihat
 * "gagal", bukan app force close. Konsekuensinya progress tidak bisa pakai
 * variabel statis biasa -> ditulis ke file kecil, dibaca UI tiap 400 ms.
 */
public final class ProgressBus {

    public static final int IDLE = 0, RUNNING = 1, DONE = 2, FAILED = 3;

    private static final String FILE = "progress.txt";

    public final int state;
    public final int pct;
    public final String text;
    public final long updatedAt;

    private ProgressBus(int state, int pct, String text, long updatedAt) {
        this.state = state;
        this.pct = pct;
        this.text = text;
        this.updatedAt = updatedAt;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static void write(Context c, int state, int pct, String text) {
        String line = state + "|" + pct + "|" + System.currentTimeMillis() + "|"
                + (text == null ? "" : text.replace('\n', ' '));
        try (FileOutputStream fos = new FileOutputStream(file(c))) {
            fos.write(line.getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    public static void running(Context c, int pct, String text) {
        write(c, RUNNING, pct, text);
    }

    public static void done(Context c, String text) {
        write(c, DONE, 100, text);
    }

    public static void failed(Context c, String text) {
        write(c, FAILED, -1, text);
    }

    public static void idle(Context c) {
        write(c, IDLE, -1, "");
    }

    public static ProgressBus read(Context c) {
        File f = file(c);
        if (!f.isFile()) return new ProgressBus(IDLE, -1, "", 0);
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            int n;
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                n = in.read(b);
            }
            String s = new String(b, 0, Math.max(n, 0), "UTF-8");
            String[] p = s.split("\\|", 4);
            return new ProgressBus(
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    p.length > 3 ? p[3] : "",
                    p.length > 2 ? Long.parseLong(p[2]) : 0);
        } catch (Exception e) {
            return new ProgressBus(IDLE, -1, "", 0);
        }
    }
}
