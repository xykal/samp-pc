package com.sampwrap.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.sampwrap.core.Manifest;
import com.sampwrap.core.PackDownloader;

import java.io.File;
import java.util.EnumSet;

/**
 * Service download paket data (Lite / Full / Extreme) di latar belakang
 * dengan progress di notifikasi.
 */
public class InstallService extends Service {

    public static final String ACTION_START = "com.sampwrap.app.DOWNLOAD";
    public static final String EXTRA_TIER = "tier";
    private static final String CHANNEL = "download";
    private static final int NOTIF_ID = 41;

    private volatile boolean running;
    private volatile boolean failed;
    private volatile String failMsg;
    private PackDownloader downloader;
    private Thread worker;

    public static void start(Context ctx, String tier) {
        Intent i = new Intent(ctx, InstallService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TIER, tier);
        ctx.startForegroundService(i);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppConfig.load(this);
        String tier = intent == null ? Prefs.pack(this) : intent.getStringExtra(EXTRA_TIER);
        if (tier == null) tier = "lite";

        createChannel();
        startForeground(NOTIF_ID, buildNotification("Menyiapkan…", 0, 0, true));

        final String chosen = tier;
        worker = new Thread(() -> download(chosen), "pack-downloader");
        worker.start();
        return START_NOT_STICKY;
    }

    private void download(String tier) {
        running = true;
        failed = false;
        ProgressBus.running(this, -1, "Menyiapkan…");
        Heartbeat hb = new Heartbeat();
        hb.start();
        boolean ok = false;
        try {
            Manifest m = ManifestStore.load(this);
            EnumSet<Manifest.Tag> tags = Manifest.tagsFor(tier);

            // Mode A: satu arsip ZIP (lebih simpel untuk hosting)
            if (!AppConfig.packArchiveUrl().isEmpty()) {
                downloadArchiveAndExtract(m, tags, tier);
                return;
            }

            File target = GameDir.get(this);

            // cek ruang sebelum mulai (biar nggak gagal di tengah 4 GB)
            String space = StorageTarget.checkSpace(target, m.totalSize(tags));
            if (space != null) throw new Exception(space.replace('\n', ' '));
            downloader = new PackDownloader(m, tags, target, new PackDownloader.Listener() {
                @Override public void onFileStart(String path, int index, int total) {
                    postProgress(basename(path), index, total, false);
                }

                @Override public void onProgress(String path, long done, long total) {
                    // progress byte -> notifikasi (tanpa progress bar detail, biar hemat)
                }

                @Override public void onFileDone(String path, boolean resumed) { }

                @Override public void onError(String path, Exception e) {
                    postFailed("Gagal: " + basename(path) + " — " + e.getMessage());
                }
            });

            PackDownloader.Stats st = downloader.run();
            if (failed) throw new Exception(failMsg == null ? "Unduhan gagal" : failMsg);
            Prefs.setPack(this, tier);
            postProgress("Selesai — game siap dijalankan", 100, 100, true);
            ok = true;
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            postFailed(msg);
        } finally {
            running = false;
            hb.halt();
            // Sukses: bersihkan notifikasi. Gagal: biarkan terlihat.
            if (ok) {
                stopForeground(true);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.cancel(NOTIF_ID);
            } else {
                stopForeground(false);
            }
            stopSelf();
        }
    }

    /**
     * Detak progress tiap 3 detik. Gunanya: kalau proses ":sync" mati mendadak
     * (crash native unrar), file progress berhenti ter-update dan UI bisa
     * menyimpulkan sendiri bahwa unduhan gagal — bukan menggantung selamanya.
     */
    private final class Heartbeat extends Thread {
        private volatile boolean live = true;
        Heartbeat() { super("progress-heartbeat"); setDaemon(true); }
        void halt() { live = false; }
        @Override public void run() {
            while (live && running) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                if (!live || !running) return;
                ProgressBus pb = ProgressBus.read(InstallService.this);
                if (pb.state == ProgressBus.RUNNING) {
                    ProgressBus.running(InstallService.this, pb.pct, pb.text);
                }
            }
        }
    }

    /**
     * Unduh satu arsip lalu ekstrak sesuai tier.
     *
     * Dua format didukung, dideteksi dari isi file (bukan dari URL):
     *   - RAR5 (paket asli Drive) -> pakai libsampunrar bawaan APK
     *   - ZIP                      -> PackInstaller
     * Setelah itu unduh & ekstrak paket client SA-MP (kecil, ~24 MB).
     */
    private void downloadArchiveAndExtract(Manifest m, EnumSet<Manifest.Tag> tags, String tier)
            throws Exception {
        File tmp = tmpDir(this);
        if (!tmp.exists() && !tmp.mkdirs()) throw new Exception("Tidak bisa membuat " + tmp);
        File arc = new File(tmp, "pack.download");

        File game = GameDir.get(this);
        String space = StorageTarget.checkSpace(game, m.totalSize(tags) + 800L * 1024 * 1024);
        if (space != null) throw new Exception(space.replace('\n', ' '));

        postProgress("Mengunduh paket…", 0, 0, true);
        downloadFile(AppConfig.packArchiveUrl(), arc);

        if (isRar(arc)) {
            installRar(arc, m, tags, tmp, game);
        } else {
            postProgress("Mengekstrak ZIP…", 0, 0, true);
            com.sampwrap.core.PackInstaller pi =
                    new com.sampwrap.core.PackInstaller(m, tags, null);
            pi.install(arc, game, false);
        }
        arc.delete();

        installClient(tmp, game);

        Prefs.setPack(this, tier);
        postProgress("Selesai — game siap dijalankan", 0, 0, true);
    }

    /** RAR bersarang: buka RAR dalam, lalu ekstrak file sesuai tier. */
    private void installRar(File outer, Manifest m, EnumSet<Manifest.Tag> tags,
                            File tmp, File game) throws Exception {
        if (!RarNative.available()) {
            throw new Exception("Extractor RAR tidak tersedia di APK ini "
                    + "(pakai APK gabungan / versi terbaru).");
        }

        postProgress("Membuka RAR dalam…", 0, 0, true);
        long[] r1 = RarNative.extract(outer.getAbsolutePath(), tmp.getAbsolutePath(),
                new String[]{"*.rar"}, null);
        if (r1 == null || r1[0] <= 0) throw new Exception("Tidak menemukan RAR di dalam paket");

        File inner = biggestRar(tmp);
        if (inner == null) throw new Exception("RAR dalam tidak terbaca");
        outer.delete();   // hemat ruang sebelum ekstrak 1,5 GB

        final int[] last = {0};
        String[] wanted = wantedPaths(m, tags);
        postProgress("Mengekstrak " + wanted.length + " file…", 0, wanted.length, false);
        long[] r2 = RarNative.extract(inner.getAbsolutePath(), game.getAbsolutePath(), wanted,
                new RarNative.Progress() {
                    @Override public void onData(String name, long done, long total) {
                        postProgress("Mengekstrak… " + (done >> 20) + " MB",
                                (int) (done >> 20), (int) (total >> 20), false);
                    }
                });
        inner.delete();
        if (r2 == null) throw new Exception("Ekstraksi gagal");
        if (r2[0] < wanted.length) {
            postProgress("Selesai " + r2[0] + "/" + wanted.length
                    + " file (sisanya tidak ada di paket)", 0, 0, true);
        }
    }

    /** Daftar path sesuai tier, tanpa file installer (samp-install/). */
    private static String[] wantedPaths(Manifest m, EnumSet<Manifest.Tag> tags) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Manifest.Entry e : m.entries()) {
            if (!tags.contains(e.tag)) continue;
            if (e.path.startsWith("samp-install/")) continue;
            out.add(e.path);
        }
        return out.toArray(new String[0]);
    }

    /** Client SA-MP hasil bedah installer (di-host terpisah, cuma ±24 MB). */
    private void installClient(File tmp, File game) throws Exception {
        String url = AppConfig.packClientUrl();
        if (url.isEmpty()) return;
        postProgress("Mengunduh client SA-MP…", 0, 0, true);
        File zip = new File(tmp, "client.zip");
        downloadFile(url, zip);
        postProgress("Memasang SA-MP…", 0, 0, true);
        unzipTo(zip, game);
        zip.delete();
    }

    private static boolean isRar(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[8];
            if (in.read(b) < 8) return false;
            // "Rar!\x1a\x07" (RAR4 maupun RAR5)
            return b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!'
                    && b[4] == 0x1a && b[5] == 0x07;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static File biggestRar(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        File best = null;
        for (File f : fs) {
            if (!f.isFile() || !f.getName().toLowerCase().endsWith(".rar")) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        return best;
    }

    private static File tmpDir(Context c) {
        File game = GameDir.get(c);
        return new File(game.getParentFile() != null ? game.getParentFile() : game, ".tmp");
    }

    /** Unduh dengan resume (HTTP Range) + progress notifikasi. */
    private void downloadFile(String url, File dst) throws Exception {
        long have = dst.isFile() ? dst.length() : 0;
        java.net.HttpURLConnection c;
        if (com.sampwrap.core.DriveDownloader.isDrive(url)) {
            c = com.sampwrap.core.DriveDownloader.open(url);
        } else {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
        }
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");

        int code = c.getResponseCode();
        if (code == 416) return;                       // sudah lengkap
        if (code != 200 && code != 206) throw new Exception("HTTP " + code);

        long len = c.getContentLengthLong();
        long total = code == 206 ? have + len : len;
        boolean append = code == 206;
        if (!append) have = 0;

        try (java.io.InputStream in = new java.io.BufferedInputStream(c.getInputStream());
             java.io.FileOutputStream fos = new java.io.FileOutputStream(dst, append)) {
            byte[] buf = new byte[128 * 1024];
            long done = have;
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    postProgress("Mengunduh " + (100 * done / total) + "%  ("
                            + (done >> 20) + "/" + (total >> 20) + " MB)",
                            (int) (done >> 20), (int) (total >> 20), false);
                }
            }
        } finally {
            c.disconnect();
        }
    }

    /** Unzip sederhana + proteksi zip-slip. */
    private static void unzipTo(File zip, File outDir) throws Exception {
        String root = outDir.getCanonicalPath() + File.separator;
        try (java.util.zip.ZipInputStream zin =
                     new java.util.zip.ZipInputStream(new java.io.FileInputStream(zip))) {
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) continue;
                File f = new File(outDir, name);
                if (!f.getCanonicalPath().startsWith(root)) continue;
                if (e.isDirectory()) { f.mkdirs(); continue; }
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }
    }

    private static String basename(String p) {
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }

    private long lastBusWrite;

    /**
     * Update progress: ke UI (lewat ProgressBus, karena service jalan di proses
     * terpisah) dan ke notifikasi. File progress ditulis maks 4x/detik supaya
     * tidak membanjiri I/O saat unduh 680 MB.
     */
    private void postProgress(String text, int index, int total, boolean done) {
        int pct = total > 0 ? (int) Math.min(100, 100L * index / total) : -1;

        long now = System.currentTimeMillis();
        if (done || now - lastBusWrite > 250) {
            lastBusWrite = now;
            if (done) {
                ProgressBus.done(this, text);
            } else {
                ProgressBus.running(this, pct, text);
            }
        }

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            try {
                nm.notify(NOTIF_ID, buildNotification(text, Math.max(pct, 0), total, done));
            } catch (Throwable ignored) {}
        }
    }

    private void postFailed(String text) {
        failed = true;
        failMsg = text;
        ProgressBus.failed(this, text);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            try { nm.notify(NOTIF_ID, buildNotification("Gagal: " + text, 0, 0, true)); }
            catch (Throwable ignored) {}
        }
    }

    private Notification buildNotification(String text, int pct, int total, boolean indeterminate) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle("SAMP PC — paket data")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(!indeterminate)
                .setContentIntent(pi);

        if (total > 0) {
            b.setProgress(100, pct, false);
        } else {
            b.setProgress(0, 0, true);
        }
        return b.build();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                "Download paket data", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Progress unduh data GTA:SA / SA-MP");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        if (downloader != null) downloader.cancel();
        super.onDestroy();
    }
}
