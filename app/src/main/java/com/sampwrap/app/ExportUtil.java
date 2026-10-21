package com.sampwrap.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Simpan file teks ke folder app + (kalau bisa) ke Downloads publik. */
public final class ExportUtil {

    private ExportUtil() {}

    public static File saveLocal(Context ctx, String dirName, String name, String content)
            throws IOException {
        File dir = new File(ctx.getExternalFilesDir(null), dirName);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("gagal buat " + dir);
        File f = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    /**
     * Ekspor ke Downloads/SAMP-PC supaya bisa dibaca app lain (Winlator, file manager).
     * Android 10+ pakai MediaStore; di bawah itu tulis langsung ke /sdcard/Download.
     */
    public static String saveToDownloads(Context ctx, String name, String content, String mime)
            throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/SAMP-PC");
            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("MediaStore menolak");
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("stream kosong");
                os.write(data);
            }
            return "Download/SAMP-PC/" + name;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "SAMP-PC");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("gagal buat " + dir);
        File f = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
        return f.getAbsolutePath();
    }
}
