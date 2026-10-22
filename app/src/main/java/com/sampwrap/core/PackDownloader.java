package com.sampwrap.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloader paket data per-file (bukan satu zip raksasa).
 *
 * Kenapa per-file:
 *  - paket GTA:SA 2-5 GB; kalau satu zip, user harus selesaikan 100% baru bisa main
 *  - dengan per-file, game bisa jalan setelah CORE selesai, radio nyusul
 *  - mendukung resume (HTTP Range) kalau koneksi putus
 *  - manifest Lite/Full cukup filter entri, bukan dua paket berbeda
 */
public final class PackDownloader {

    public interface Listener {
        void onFileStart(String path, int index, int total);
        void onProgress(String path, long done, long total);
        void onFileDone(String path, boolean resumed);
        void onError(String path, Exception e);
    }

    private static final int BUFFER = 128 * 1024;

    private final Manifest manifest;
    private final java.util.EnumSet<Manifest.Tag> included;
    private final File targetDir;
    private final Listener listener;
    private volatile boolean cancelled;

    public PackDownloader(Manifest manifest,
                          java.util.EnumSet<Manifest.Tag> included,
                          File targetDir,
                          Listener listener) {
        this.manifest = manifest;
        this.included = included;
        this.targetDir = targetDir;
        this.listener = listener;
    }

    public void cancel() {
        cancelled = true;
    }

    /** Unduh semua file sesuai plan. Melempar exception pertama yang fatal. */
    public Stats run() throws Exception {
        Stats st = new Stats();
        java.util.List<Manifest.Entry> plan = manifest.plan(included);
        st.filesTotal = plan.size();

        for (int i = 0; i < plan.size(); i++) {
            if (cancelled) break;
            Manifest.Entry e = plan.get(i);
            if (e.url == null || e.url.isEmpty() || e.url.equals("-")) {
                st.skipped++;
                continue;
            }
            File out = new File(targetDir, e.path);
            if (out.exists() && e.size > 0 && out.length() == e.size) {
                st.skipped++;
                st.bytes += e.size;
                continue; // sudah lengkap
            }
            if (listener != null) listener.onFileStart(e.path, i + 1, plan.size());
            try {
                boolean resumed = downloadOne(e, out);
                st.done++;
                st.bytes += out.length();
                if (listener != null) listener.onFileDone(e.path, resumed);
            } catch (Exception ex) {
                st.failed++;
                if (listener != null) listener.onError(e.path, ex);
                throw ex;
            }
        }
        return st;
    }

    private boolean downloadOne(Manifest.Entry e, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("tidak bisa buat folder " + parent);
        }
        File part = new File(out.getAbsolutePath() + ".part");

        // Sisa sesi lama yang ternyata halaman HTML bukan potongan file.
        if (part.isFile() && ResumableDownload.looksLikeHtml(part)) {
            if (!part.delete()) throw new IOException("tidak bisa hapus " + part);
        }

        // 416 / Content-Range tidak cocok: buang .part lalu ulang sekali dari nol.
        for (int attempt = 0; ; attempt++) {
            boolean last = attempt >= 1;
            long have = part.exists() ? part.length() : 0;
            boolean resumed = have > 0;

            HttpURLConnection c = (HttpURLConnection) new URL(e.url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            HttpTune.apply(c);
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");

            int code;
            String cr;
            long len;
            try {
                code = ResumePolicy.status(c);
                cr = c.getHeaderField("Content-Range");
                len = c.getContentLengthLong();
            } catch (IOException ex) {
                // JVM yang melempar untuk 4xx: tetap baca kodenya, jangan menyerah
                int sc = ex instanceof HttpStatusException
                        ? ((HttpStatusException) ex).code
                        : ResumePolicy.codeFromMessage(ex.getMessage());
                if (sc <= 0) {
                    c.disconnect();
                    throw ex;
                }
                code = sc;
                cr = null;
                len = -1;
            }

            // Manifest sudah memberi ukuran pasti: pakai itu kalau server tidak
            // menyertakan Content-Range pada 416 (mis. Google Drive).
            long totalKnown = ResumePolicy.totalFromContentRange(cr);
            if (code == 416 && totalKnown <= 0) totalKnown = e.size;

            ResumePolicy.Action action = (code == 416 && totalKnown > 0)
                    ? (have == totalKnown ? ResumePolicy.Action.COMPLETE
                                          : ResumePolicy.Action.RESTART)
                    : ResumePolicy.decide(have, code, cr, len);

            if (action == ResumePolicy.Action.COMPLETE) {
                c.disconnect();
                if (out.exists() && !out.delete()) throw new IOException("tidak bisa hapus " + out);
                if (!part.renameTo(out)) throw new IOException("gagal rename " + part);
                return true;
            }

            boolean needReopen = (code == 416)
                    || (code == 206 && have > 0
                        && ResumePolicy.startFromContentRange(cr) >= 0
                        && ResumePolicy.startFromContentRange(cr) != have);

            if (needReopen) {
                c.disconnect();
                if (last) throw new IOException("server menolak resume (HTTP " + code + ") untuk " + e.path);
                if (part.exists() && !part.delete()) throw new IOException("tidak bisa hapus " + part);
                continue;   // ulang dari byte 0
            }

            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                c.disconnect();
                throw new IOException("HTTP " + code + " untuk " + e.path);
            }

            boolean append = code == HttpURLConnection.HTTP_PARTIAL && have > 0;
            if (!append) {
                have = 0;
                resumed = false;
            }

            long total = e.size > 0 ? e.size : c.getContentLengthLong() + have;
            long done = have;

            try (InputStream in = new BufferedInputStream(c.getInputStream(), BUFFER);
                 FileOutputStream fos = new FileOutputStream(part, append)) {
                byte[] buf = new byte[BUFFER];
                int n;
                long lastNotify = 0;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled) throw new IOException("dibatalkan");
                    fos.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (listener != null && (now - lastNotify > 250 || done == total)) {
                        listener.onProgress(e.path, done, total);
                        lastNotify = now;
                    }
                }
            } finally {
                c.disconnect();
            }

            if (e.size > 0 && part.length() != e.size) {
                throw new IOException("ukuran tidak cocok: " + e.path
                        + " (" + part.length() + " != " + e.size + ")");
            }

            if (e.sha256 != null && !e.sha256.equals("-") && !e.sha256.isEmpty()) {
                final String actual;
                try {
                    actual = PackInstaller.sha256(part);
                } catch (Exception ex) {
                    throw new IOException("gagal hitung sha256: " + e.path, ex);
                }
                if (!actual.equalsIgnoreCase(e.sha256)) {
                    throw new IOException("sha256 tidak cocok: " + e.path);
                }
            }

            // rename atomik
            if (out.exists() && !out.delete()) throw new IOException("tidak bisa hapus " + out);
            if (!part.renameTo(out)) throw new IOException("gagal rename " + part);
            return resumed;
        }
    }

    /** Hapus file yang tidak termasuk plan (dipakai saat downgrade Full -> Lite). */
    public int prune() {
        int n = 0;
        for (Manifest.Entry e : manifest.entries()) {
            if (included.contains(e.tag)) continue;
            File f = new File(targetDir, e.path);
            if (f.exists() && f.delete()) n++;
        }
        return n;
    }

    /** Hapus file .part yang tersisa (user batalkan / error). */
    public static void cleanupPartials(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) cleanupPartials(f);
            else if (f.getName().endsWith(".part")) f.delete();
        }
    }

    /** Berapa byte yang sudah ada di disk untuk plan ini. */
    public long presentBytes() {
        long total = 0;
        for (Manifest.Entry e : manifest.plan(included)) {
            File f = new File(targetDir, e.path);
            if (f.exists()) total += f.length();
        }
        return total;
    }

    public static final class Stats {
        public int filesTotal;
        public int done;
        public int skipped;
        public int failed;
        public long bytes;

        @Override
        public String toString() {
            return done + " diunduh, " + skipped + " sudah ada, "
                    + failed + " gagal, " + Manifest.humanSize(bytes) + " total";
        }
    }
}
