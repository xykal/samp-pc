package com.sampwrap.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unzip sederhana + proteksi zip-slip. Tanpa library eksternal. */
public final class ZipUtil {

    private ZipUtil() {}

    public static List<File> unzip(File zip, File outDir) throws IOException {
        List<File> files = new ArrayList<>();
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("gagal buat " + outDir);
        String base = outDir.getCanonicalPath();

        try (ZipFile zf = new ZipFile(zip)) {
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                File out = new File(outDir, e.getName());
                if (!out.getCanonicalPath().startsWith(base)) continue; // zip-slip
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream in = new BufferedInputStream(zf.getInputStream(e));
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                files.add(out);
            }
        }
        return files;
    }

    /** Nama mod dari nama file zip: "SpeedoPack_v2.zip" -> "SpeedoPack_v2". */
    public static String modName(File zip) {
        String n = zip.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
