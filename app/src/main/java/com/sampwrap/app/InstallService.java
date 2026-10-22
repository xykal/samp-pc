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
    public static final String EXTRA_URL = "pack_url";
    public static final String EXTRA_MODE = "storage_mode";
    public static final String EXTRA_PATH = "local_path";
    public static final String ACTION_LOCAL = "com.sampwrap.app.INSTALL_LOCAL";
    private static final String CHANNEL = "download";
    private static final int NOTIF_ID = 41;

    /** URL paket yang dipakai proses ini (dari Intent, bukan prefs). */
    private String packUrl = "";
    private String localPath = null;
    private volatile boolean running;
    private volatile boolean failed;
    private volatile String failMsg;
    private PackDownloader downloader;
    private Thread worker;

    public static void start(Context ctx, String tier) {
        Intent i = new Intent(ctx, InstallService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TIER, tier);
        // dikirim lewat Intent: SharedPreferences tidak sinkron antar-proses,
        // jadi service (:sync) tidak boleh mengandalkan prefs milik UI
        i.putExtra(EXTRA_URL, Prefs.effectivePackUrl(ctx));
        i.putExtra(EXTRA_MODE, StorageTarget.currentMode(ctx).name());
        ctx.startForegroundService(i);
    }

    /** Pasang dari file yang sudah ada di HP (ZIP atau RAR) — tanpa unduh ulang. */
    public static void startLocal(Context ctx, String tier, File file) {
        Intent i = new Intent(ctx, InstallService.class);
        i.setAction(ACTION_LOCAL);
        i.putExtra(EXTRA_TIER, tier);
        i.putExtra(EXTRA_PATH, file.getAbsolutePath());
        i.putExtra(EXTRA_MODE, StorageTarget.currentMode(ctx).name());
        i.putExtra(EXTRA_URL, Prefs.effectivePackUrl(ctx));
        ctx.startForegroundService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // proses :sync punya crash handler sendiri, biar errornya kelihatan
        CrashGuard.install(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            return onStartSafe(intent);
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            InstallLog.w(this, "service gagal start: " + msg);
            postFailed("Service gagal start: " + msg);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private int onStartSafe(Intent intent) {
        AppConfig.load(this);
        if (intent != null) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null) packUrl = url;
            localPath = intent.getStringExtra(EXTRA_PATH);
            String mode = intent.getStringExtra(EXTRA_MODE);
            if (mode != null) {
                try { StorageTarget.setMode(this, StorageTarget.Mode.valueOf(mode)); }
                catch (Throwable ignored) {}
            }
        }
        if (packUrl == null || packUrl.isEmpty()) packUrl = Prefs.effectivePackUrl(this);
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
        InstallLog.w(this, "=== mulai tier " + tier + " ===");
        InstallLog.w(this, "sumber: " + (localPath != null ? "file lokal " + localPath
                : (packUrl == null || packUrl.isEmpty()
                ? "(per-file) " + AppConfig.packBaseUrl() : packUrl)));
        Heartbeat hb = new Heartbeat();
        hb.start();
        boolean ok = false;
        try {
            Manifest m = ManifestStore.load(this);
            EnumSet<Manifest.Tag> tags = Manifest.tagsFor(tier);

            // Mode lokal: file sudah ada di HP (mis. diunduh lewat browser)
            if (localPath != null) {
                File f = new File(localPath);
                if (!f.isFile() || f.length() < 1024) {
                    throw new Exception("File tidak ditemukan: " + localPath);
                }
                InstallLog.w(this, "file lokal " + f.getName() + " = " + f.length() + " B");
                installArchive(f, m, tags, tier);
                f.delete();
                ok = true;
                return;
            }

            // Mode A: satu arsip ZIP/RAR
            if (packUrl != null && !packUrl.isEmpty()) {
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
            InstallLog.w(this, "GAGAL: " + msg);
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

        postProgress("Mengunduh paket…", -1, 0, false);
        downloadFile(packUrl, arc, "paket",
                m.totalSize(tags) + 300L * 1024 * 1024);

        // arsip TIDAK dihapus kalau masih gagal — biar bisa diulang tanpa unduh ulang
        installArchive(arc, m, tags, tier);
        arc.delete();
    }

    /**
     * Pasang dari satu arsip (RAR berlapis atau ZIP) lalu tempel client SA-MP.
     * Dipakai untuk hasil unduhan maupun file impor dari HP.
     */
    private void installArchive(File arc, Manifest m, EnumSet<Manifest.Tag> tags, String tier)
            throws Exception {
        File game = GameDir.get(this);
        File tmp = tmpDir(this);
        if (!tmp.exists() && !tmp.mkdirs()) throw new Exception("Tidak bisa membuat " + tmp);

        InstallLog.w(this, "arsip: " + arc.getName() + " (" + (arc.length() >> 20) + " MB)"
                + " rar=" + isRar(arc));

        if (isRar(arc)) {
            installRar(arc, m, tags, tmp, game);
        } else {
            postProgress("Mengekstrak ZIP…", -1, 0, false);
            InstallLog.w(this, "ekstrak ZIP (PackInstaller)");
            com.sampwrap.core.PackInstaller pi =
                    new com.sampwrap.core.PackInstaller(m, tags, null);
            pi.install(arc, game, false);
        }

        installClient(tmp, game);

        Prefs.setPack(this, tier);
        long size = GameDir.size(game);
        InstallLog.w(this, "selesai, ukuran folder=" + size + " B");
        InstallLog.w(this, "isi folder: " + topLevel(game));

        // pagar terakhir: kalau hasil ekstrak tidak sesuai harapan, jangan
        // bilang "selesai" — user jadi bingung kenapa samp.exe tidak ada
        if (!new File(game, "samp.exe").isFile()) {
            throw new Exception("Selesai mengekstrak (" + (size >> 20) + " MB) tapi samp.exe "
                    + "tidak ada. Isi folder: " + topLevel(game));
        }
        postProgress("Selesai — game siap dijalankan", 100, 100, true);
    }

    /** RAR bersarang: buka RAR dalam, lalu ekstrak file sesuai tier. */
    private void installRar(File outer, Manifest m, EnumSet<Manifest.Tag> tags,
                            File tmp, File game) throws Exception {
        boolean rarOk = RarNative.available();
        InstallLog.w(this, "RarNative tersedia=" + rarOk);
        if (!rarOk) {
            throw new Exception("Extractor RAR tidak bisa dipakai di perangkat ini "
                    + "(libsampunrar gagal dimuat). Di emulator x86, jalankan di HP ARM64.");
        }

        postProgress("Membuka RAR dalam…", -1, 0, false);
        InstallLog.w(this, "pass 1: cari *.rar di " + outer.getName());
        long[] r1 = RarNative.extract(outer.getAbsolutePath(), tmp.getAbsolutePath(),
                new String[]{"*.rar"}, null);
        InstallLog.w(this, "pass 1 hasil=" + (r1 == null ? "null" : (r1[0] + " file, " + r1[1] + " B")));
        if (r1 == null || r1[0] <= 0) throw new Exception("Tidak menemukan RAR di dalam paket");

        File inner = biggestRar(tmp);
        if (inner == null) throw new Exception("RAR dalam tidak terbaca");
        InstallLog.w(this, "rar dalam: " + inner.getName() + " (" + (inner.length() >> 20) + " MB)");
        // luar tidak dihapus di sini: kalau pass 2 gagal, tidak perlu unduh 712 MB lagi

        final int[] last = {0};
        String[] wanted = wantedPaths(m, tags);
        postProgress("Mengekstrak " + wanted.length + " file…", 0, wanted.length, false);
        InstallLog.w(this, "pass 2: " + wanted.length + " file -> " + game.getAbsolutePath());
        long[] r2 = RarNative.extract(inner.getAbsolutePath(), game.getAbsolutePath(), wanted,
                new RarNative.Progress() {
                    private long lastLog;
                    @Override public void onData(String name, long done, long total) {
                        postProgress("Mengekstrak… " + (done >> 20) + " MB",
                                (int) (done >> 20), (int) (total >> 20), false);
                        if (done - lastLog > 200L * 1024 * 1024) {
                            lastLog = done;
                            InstallLog.w(InstallService.this, "ekstrak " + (done >> 20) + " MB / "
                                    + (total >> 20) + " MB  (" + name + ")");
                        }
                    }
                });
        InstallLog.w(this, "pass 2 hasil=" + (r2 == null ? "null" : (r2[0] + " file, " + r2[1] + " B")));
        inner.delete();
        if (r2 == null) throw new Exception("Ekstraksi gagal (lib unrar mengembalikan null)");
        if (r2[0] < wanted.length) {
            postProgress("Selesai " + r2[0] + "/" + wanted.length
                    + " file (sisanya tidak ada di paket)", -1, 0, false);
        }
    }

    private static String topLevel(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) return "(kosong)";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (File f : fs) {
            if (n++ > 0) sb.append(", ");
            sb.append(f.getName()).append(f.isDirectory() ? "/" : "");
            if (n >= 14) { sb.append(", …"); break; }
        }
        return sb.toString();
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
        postProgress("Mengunduh client SA-MP…", -1, 0, false);
        File zip = new File(tmp, "client.zip");
        downloadFile(url, zip, "client SA-MP", 0);
        postProgress("Memasang SA-MP…", -1, 0, false);
        InstallLog.w(this, "unzip client -> " + game.getAbsolutePath());
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

    /**
     * Unduh satu file besar: resume (HTTP Range), retry, dan pemulihan 416.
     *
     * Logika range-nya ada di com.sampwrap.core.ResumableDownload (murni Java,
     * ada test-nya di PC). Yang penting di sini:
     *   - 416 TIDAK dianggap "sudah lengkap" (itu bug lama: file sementara
     *     yang lebih besar dari file asli bikin 416, lalu ekstraksi gagal);
     *   - hasil unduhan dicek magic-nya (RAR!/PK). Kalau ternyata bukan arsip,
     *     file dibuang dan diunduh ulang dari nol — bukan diteruskan ke ekstraktor.
     */
    private void downloadFile(final String url, final File dst, final String label,
                              long extraNeeded) throws Exception {
        final boolean drive = com.sampwrap.core.DriveDownloader.isDrive(url);

        com.sampwrap.core.ResumableDownload.Source src =
                new com.sampwrap.core.ResumableDownload.Source() {
                    @Override public java.net.HttpURLConnection open(long start)
                            throws java.io.IOException {
                        if (drive) return com.sampwrap.core.DriveDownloader.open(url, start);
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                                new java.net.URL(url).openConnection();
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(45000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SAMP-PC");
                        if (start > 0) c.setRequestProperty("Range", "bytes=" + start + "-");
                        return c;
                    }
                };

        com.sampwrap.core.ResumableDownload.Listener listener =
                new com.sampwrap.core.ResumableDownload.Listener() {
                    @Override public void onLog(String msg) {
                        InstallLog.w(InstallService.this, "  " + msg);
                    }

                    @Override public void onProgress(long done, long total) {
                        if (total > 0) {
                            postProgress("Mengunduh " + label + " " + (100 * done / total) + "%  ("
                                            + (done >> 20) + "/" + (total >> 20) + " MB)",
                                    (int) (done >> 20), (int) (total >> 20), false);
                        } else {
                            postProgress("Mengunduh " + label + "  (" + (done >> 20) + " MB)",
                                    -1, 0, false);
                        }
                    }
                };

        InstallLog.w(this, "unduh " + label + " (" + (drive ? "Drive" : "URL") + ") -> " + dst);
        com.sampwrap.core.ResumableDownload.fetch(dst, src, 6, listener);

        if (!looksLikeArchive(dst)) {
            InstallLog.w(this, "isi bukan arsip (magic salah) -> buang, ulang dari nol");
            long bad = dst.length();
            if (!dst.delete()) throw new Exception("File hasil unduhan rusak dan tidak bisa dihapus: " + dst);
            postProgress("File hasil unduhan rusak (" + (bad >> 20) + " MB) — ulang dari nol…",
                    -1, 0, false);
            com.sampwrap.core.ResumableDownload.fetch(dst, src, 6, listener);
            if (!looksLikeArchive(dst)) {
                throw new Exception("Unduhan selesai (" + (dst.length() >> 20) + " MB) tapi isinya "
                        + "bukan arsip RAR/ZIP. Biasanya Drive mengirim halaman lain (kuota) — "
                        + "coba lagi nanti atau impor file RAR manual.");
            }
        }
        InstallLog.w(this, "  selesai " + dst.length() + " byte");
    }

    /** RAR ("Rar!") atau ZIP ("PK\x03\x04") — dua format paket yang didukung. */
    private static boolean looksLikeArchive(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[8];
            int n = in.read(b);
            if (n < 4) return false;
            if (b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!') return true;
            return b[0] == 'P' && b[1] == 'K'
                    && (b[2] == 3 || b[2] == 5 || b[2] == 7);
        } catch (java.io.IOException e) {
            return false;
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
                .setContentTitle(AppConfig.appLabel() + " — paket data")
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
