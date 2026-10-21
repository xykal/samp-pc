package com.sampwrap.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ekstrak paket data (zip) ke folder game, mengikuti plan Lite/Full.
 *
 * Di Android dipanggil dari Service + progress notification; di sini core-nya
 * murni java.io supaya bisa diuji unit test tanpa device.
 */
public final class PackInstaller {

    public interface Listener {
        void onProgress(String currentFile, long doneBytes, long totalBytes);
        void onFileDone(String path, boolean skipped);
    }

    private final Manifest manifest;
    private final EnumSet<Manifest.Tag> included;
    private final Listener listener;

    public PackInstaller(Manifest manifest, EnumSet<Manifest.Tag> included, Listener listener) {
        this.manifest = manifest;
        this.included = included;
        this.listener = listener;
    }

    /**
     * @param zip      paket data (boleh zip utuh; file yang tidak termasuk plan dilewati)
     * @param targetDir folder game (di dalam container: drive_c/Games/GTA San Andreas)
     * @param dryRun   true = hitung saja, tidak menulis apa pun
     */
    public Result install(File zip, File targetDir, boolean dryRun) throws IOException {
        Result res = new Result();
        long total = manifest.totalSize(included);
        long done = 0;

        try (ZipFile zf = new ZipFile(zip)) {
            var it = zf.entries();
            while (it.hasMoreElements()) {
                ZipEntry ze = it.nextElement();
                if (ze.isDirectory()) continue;

                String path = ze.getName().replace('\\', '/');
                res.entriesTotal++;

                boolean includedFile = manifest.includes(included, path);
                if (!includedFile) {
                    res.skipped++;
                    if (listener != null) listener.onFileDone(path, true);
                    continue;
                }

                File out = new File(targetDir, path);
                // jangan pernah keluar dari targetDir (zip-slip protection)
                if (!out.getCanonicalPath().startsWith(targetDir.getCanonicalPath())) {
                    res.rejected++;
                    continue;
                }

                if (!dryRun) {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Tidak bisa membuat folder: " + parent);
                    }
                    copy(ze, zf, out);
                }

                done += ze.getSize() > 0 ? ze.getSize() : 0;
                res.written++;
                res.bytesWritten += ze.getSize() > 0 ? ze.getSize() : 0;
                if (listener != null) listener.onProgress(path, done, total);
            }
        }
        res.expectedBytes = total;
        return res;
    }

    private static void copy(ZipEntry ze, ZipFile zf, File out) throws IOException {
        try (InputStream in = new BufferedInputStream(zf.getInputStream(ze));
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    /** SHA-256 file; bandingkan dengan Manifest.Entry.sha256 kalau mau verifikasi. */
    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(f))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static final class Result {
        public int entriesTotal;
        public int written;
        public int skipped;
        public int rejected;
        public long bytesWritten;
        public long expectedBytes;

        @Override
        public String toString() {
            return String.format(
                    "ditulis=%d dilewati=%d ditolak=%d  %s / %s",
                    written, skipped, rejected,
                    Manifest.humanSize(bytesWritten),
                    Manifest.humanSize(expectedBytes));
        }
    }
}
