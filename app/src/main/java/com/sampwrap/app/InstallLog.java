package com.sampwrap.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Log langkah unduhan/instalasi ke file.
 *
 * Tujuannya satu: kalau gagal di HP user, penyebabnya bisa dibaca tanpa
 * nebak-nebak. Isinya kecil (maks 256 KB) dan bisa dibuka/dibagikan dari app.
 */
public final class InstallLog {

    private static final String FILE = "install.log";
    private static final long MAX_BYTES = 256 * 1024;
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private InstallLog() {}

    public static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static synchronized void w(Context c, String line) {
        String row = TS.format(new Date()) + "  " + line + "\n";
        try {
            File f = file(c);
            if (f.length() > MAX_BYTES) {
                // potong separuh biar tidak tumbuh terus
                byte[] all = new byte[(int) f.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    int n = in.read(all);
                    if (n > 0) {
                        String s = new String(all, 0, n, "UTF-8");
                        String keep = s.substring(Math.min(s.length(), s.length() / 2));
                        try (FileOutputStream out = new FileOutputStream(f, false)) {
                            out.write(keep.getBytes("UTF-8"));
                        }
                    }
                }
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(row.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {}
    }

    public static String read(Context c) {
        File f = file(c);
        if (!f.isFile() || f.length() == 0) return "(log kosong)";
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 256 * 1024)];
            int n;
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                n = in.read(b);
            }
            String s = new String(b, 0, Math.max(n, 0), "UTF-8");
            // tampilkan bagian terakhir saja kalau kepanjangan
            return s.length() > 6000 ? "…\n" + s.substring(s.length() - 6000) : s;
        } catch (Throwable t) {
            return "(gagal baca log: " + t + ")";
        }
    }

    public static void clear(Context c) {
        file(c).delete();
    }
}
