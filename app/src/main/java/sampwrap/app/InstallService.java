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
            if (space != null) {
                postProgress(space.replace("\n", " "), 0, 0, true);
                return;
            }
            downloader = new PackDownloader(m, tags, target, new PackDownloader.Listener() {
                @Override public void onFileStart(String path, int index, int total) {
                    postProgress(basename(path), index, total, false);
                }

                @Override public void onProgress(String path, long done, long total) {
                    // progress byte -> notifikasi (tanpa progress bar detail, biar hemat)
                }

                @Override public void onFileDone(String path, boolean resumed) { }

                @Override public void onError(String path, Exception e) {
                    postProgress("Gagal: " + basename(path) + " — " + e.getMessage(), 0, 0, true);
                }
            });

            PackDownloader.Stats st = downloader.run();
            Prefs.setPack(this, tier);
            postProgress("Selesai: " + st.toString(), 0, 0, true);
        } catch (Exception e) {
            postProgress("Gagal: " + e.getMessage(), 0, 0, true);
        } finally {
            running = false;
            stopForeground(false);
            stopSelf();
        }
    }

    /** Unduh satu ZIP lalu ekstrak sesuai tier (memakai PackInstaller). */
    private void downloadArchiveAndExtract(Manifest m, EnumSet<Manifest.Tag> tags, String tier)
            throws Exception {
        File cache = new File(getCacheDir(), "pack.zip");
        postProgress("Mengunduh arsip…", 0, 0, true);

        java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                new java.net.URL(AppConfig.packArchiveUrl()).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        long len = c.getContentLengthLong();
        try (java.io.InputStream in = new java.io.BufferedInputStream(c.getInputStream());
             java.io.FileOutputStream fos = new java.io.FileOutputStream(cache)) {
            byte[] buf = new byte[128 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                if (len > 0) postProgress("Arsip " + (100 * done / len) + "%", (int) done, (int) len, false);
            }
        } finally {
            c.disconnect();
        }

        postProgress("Mengekstrak…", 0, 0, true);
        com.sampwrap.core.PackInstaller pi =
                new com.sampwrap.core.PackInstaller(m, tags, null);
        com.sampwrap.core.PackInstaller.Result r =
                pi.install(cache, GameDir.get(this), false);
        Prefs.setPack(this, tier);
        cache.delete();
        postProgress("Selesai: " + r, 0, 0, true);
    }

    private static String basename(String p) {
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }

    private void postProgress(String text, int index, int total, boolean done) {
        int pct = total > 0 ? (int) (100L * index / total) : 0;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text, pct, total, done));
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
