package com.sampwrap.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Penangkap crash: stack trace disimpan ke file, lalu bisa dilihat/dibagikan
 * dari dalam app. Tanpa ini, "force close" cuma jadi tebakan.
 */
public final class CrashGuard {

    private static final String FILE = "last_crash.txt";
    private static boolean installed;

    private CrashGuard() {}

    public static void install(final Context ctx) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { save(ctx.getApplicationContext(), t, e); } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private static void save(Context ctx, Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Waktu : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Thread: " + t.getName());
        pw.println("HP    : " + Build.MANUFACTURER + " " + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("ABI   : " + android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS));
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        try (java.io.FileOutputStream fos =
                     new java.io.FileOutputStream(new File(ctx.getFilesDir(), FILE))) {
            fos.write(sw.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    /** Isi crash terakhir, atau null kalau bersih. */
    public static String last(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile() || f.length() == 0) return null;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 64 * 1024)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = in.read(b);
                return new String(b, 0, Math.max(n, 0), "UTF-8");
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(Context ctx) {
        new File(ctx.getFilesDir(), FILE).delete();
    }
}
