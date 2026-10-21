package com.sampwrap.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sampwrap.core.ContainerPreset;
import com.sampwrap.core.DeviceProfile;
import com.sampwrap.core.Manifest;
import com.sampwrap.core.ModInstaller;
import com.sampwrap.core.PackInstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PACK_ZIP = 1001;
    private static final int REQ_MOD_ZIP = 1002;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView deviceText;
    private TextView tierBadge;
    private TextView verdictText;
    private TextView storageText;

    private DeviceProfile.Spec spec;
    private DeviceProfile.Preset preset;
    private Manifest manifest;

    private UiDialog.Progress liveProgress;
    private volatile boolean progressHidden;
    private final Runnable progressPoll = new Runnable() {
        @Override public void run() {
            ProgressBus pb = ProgressBus.read(MainActivity.this);
            if (pb.state == ProgressBus.RUNNING
                    && System.currentTimeMillis() - pb.updatedAt > 25000) {
                // proses ":sync" mati mendadak (crash native) -> jangan menggantung
                hideProgress();
                ProgressBus.failed(MainActivity.this,
                        "Proses berhenti mendadak saat: " + pb.text);
                ui.post(this);
                return;
            }
            if (pb.state == ProgressBus.RUNNING) {
                if (!progressHidden) {
                    if (liveProgress == null) {
                        liveProgress = UiDialog.progress(MainActivity.this,
                                "Mengunduh paket data", pb.text);
                        liveProgress.onCancel(() -> progressHidden = true);
                        liveProgress.show();
                    }
                    liveProgress.show();
                    liveProgress.update(pb.text, pb.pct);
                }
                ui.postDelayed(this, 400);
            } else if (pb.state == ProgressBus.DONE) {
                hideProgress();
                progressHidden = false;
                ProgressBus.idle(MainActivity.this);
                refreshStorage();
                new UiDialog.Alert(MainActivity.this)
                        .title("Paket siap")
                        .message("Data sudah diekstrak.\n\nTekan \"Jalankan SA-MP\" untuk mulai.")
                        .positive("Oke", null)
                        .show();
            } else if (pb.state == ProgressBus.FAILED) {
                hideProgress();
                progressHidden = false;
                ProgressBus.idle(MainActivity.this);
                new UiDialog.Alert(MainActivity.this)
                        .title("Gagal mengunduh")
                        .message(pb.text)
                        .negative("Lihat log", () -> showCrashLog())
                        .positive("Tutup", null)
                        .show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashGuard.install(this);
        AppConfig.load(this);
        setContentView(buildUi());
        askNotificationPermission();
        scanDevice();
        loadManifest();
        showLastCrashIfAny();
        ui.post(progressPoll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (manifest != null) refreshStorage();
        ui.removeCallbacks(progressPoll);
        ui.post(progressPoll);
    }

    private void hideProgress() {
        if (liveProgress != null) {
            liveProgress.dismiss();
            liveProgress = null;
        }
    }

    /** Tampilkan crash terakhir (kalau ada) supaya kelihatan penyebabnya. */
    private void showLastCrashIfAny() {
        final String log = CrashGuard.last(this);
        if (log == null) return;
        new UiDialog.Alert(this)
                .title("App terakhir crash")
                .message(log.length() > 1200 ? log.substring(0, 1200) + "\n…" : log)
                .negative("Hapus", () -> CrashGuard.clear(this))
                .positive("Tutup", null)
                .show();
    }

    private void showCrashLog() {
        String log = CrashGuard.last(this);
        new UiDialog.Alert(this)
                .title("Log terakhir")
                .message(log == null ? "Tidak ada log." : log)
                .positive("Tutup", null)
                .show();
    }

    // ------------------------------------------------------------------ UI ----

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiKit.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(this, 18);
        root.setPadding(p, p, p, p);

        TextView title = UiKit.text(this, AppConfig.appLabel(), 28, true, UiKit.ACCENT);
        TextView sub = UiKit.text(this,
                "SA-MP versi PC (x86) lewat Wine + Box64", 13, false, UiKit.MUTED);
        root.addView(title);
        root.addView(sub);
        root.addView(UiKit.gap(this, 16));

        // --- kartu perangkat ---
        LinearLayout devCard = UiKit.card(this);
        devCard.addView(UiKit.text(this, "PERANGKAT", 12, true, UiKit.MUTED));
        devCard.addView(UiKit.gap(this, 8));

        deviceText = UiKit.text(this, "Memindai…", 14, false, UiKit.TEXT);
        devCard.addView(deviceText);
        devCard.addView(UiKit.gap(this, 10));

        tierBadge = UiKit.badge(this, "…", UiKit.MUTED);
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.addView(tierBadge);
        devCard.addView(badgeRow);

        devCard.addView(UiKit.gap(this, 10));
        verdictText = UiKit.text(this, "", 13, false, UiKit.MUTED);
        devCard.addView(verdictText);
        root.addView(devCard);

        root.addView(UiKit.gap(this, 14));

        // --- paket data ---
        LinearLayout packCard = UiKit.card(this);
        packCard.addView(UiKit.text(this, "PAKET DATA", 12, true, UiKit.MUTED));
        packCard.addView(UiKit.gap(this, 8));
        storageText = UiKit.text(this, "Menghitung…", 13, false, UiKit.TEXT);
        packCard.addView(storageText);
        packCard.addView(UiKit.gap(this, 12));

        packCard.addView(action("Pilih & unduh paket", true, v -> showPackDialog()));
        packCard.addView(UiKit.gap(this, 8));
        packCard.addView(action("Impor dari file ZIP", false, v -> pickZip(REQ_PACK_ZIP)));
        packCard.addView(UiKit.gap(this, 8));
        packCard.addView(action("Lokasi penyimpanan", false, v -> showStorageDialog()));
        root.addView(packCard);

        root.addView(UiKit.gap(this, 14));

        // --- mod ---
        LinearLayout modCard = UiKit.card(this);
        modCard.addView(UiKit.text(this, "MOD", 12, true, UiKit.MUTED));
        modCard.addView(UiKit.gap(this, 8));
        modCard.addView(UiKit.text(this,
                "Install .asi / .cs / .txd / .dff dari file ZIP.\n"
                        + "File lama otomatis dibackup ke mods/_backup.", 13, false, UiKit.TEXT));
        modCard.addView(UiKit.gap(this, 12));
        modCard.addView(action("Pasang mod (ZIP)", false, v -> pickZip(REQ_MOD_ZIP)));
        root.addView(modCard);

        root.addView(UiKit.gap(this, 14));

        // --- preset & jalankan ---
        LinearLayout runCard = UiKit.card(this);
        runCard.addView(UiKit.text(this, "JALANKAN", 12, true, UiKit.MUTED));
        runCard.addView(UiKit.gap(this, 8));
        runCard.addView(action("Jalankan SA-MP", true, v -> launchGame()));
        runCard.addView(UiKit.gap(this, 8));
        runCard.addView(action("Atur kontrol", false, v ->
                startActivity(new Intent(MainActivity.this, ControlsActivity.class))));
        runCard.addView(action("Ekspor preset container", false, v -> exportPreset()));
        runCard.addView(action("Buka folder game", false, v -> showGameFolder()));
        root.addView(runCard);

        root.addView(UiKit.gap(this, 18));
        root.addView(UiKit.text(this,
                "Catatan: file game GTA:SA (versi PC 1.0) tidak disertakan. "
                        + "Gunakan salinan yang kamu miliki sendiri.", 11, false, UiKit.MUTED));

        scroll.addView(root);
        return scroll;
    }

    private View action(String label, boolean primary, View.OnClickListener l) {
        View b = UiKit.button(this, label, primary);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }

    // -------------------------------------------------------------- device ----

    private void scanDevice() {
        new Thread(() -> {
            DeviceProfile.Spec s = DeviceScan.spec(this);
            DeviceProfile.Preset pr = DeviceProfile.pick(s);
            ui.post(() -> {
                spec = s;
                preset = pr;
                deviceText.setText(DeviceScan.describe(s));
                tierBadge.setText(pr.tier);
                tierBadge.setTextColor(pr.viable ? UiKit.OK : UiKit.DANGER);
                verdictText.setText(DeviceProfile.verdict(s, pr));
                verdictText.setTextColor(pr.viable ? UiKit.MUTED : UiKit.DANGER);
            });
        }, "device-scan").start();
    }

    private void loadManifest() {
        new Thread(() -> {
            try {
                Manifest m = ManifestStore.load(this);
                ui.post(() -> {
                    manifest = m;
                    refreshStorage();
                });
            } catch (Exception e) {
                ui.post(() -> toast("Gagal baca manifest: " + e.getMessage()));
            }
        }, "manifest").start();
    }

    private void refreshStorage() {
        if (manifest == null) return;
        long full = manifest.totalSize(Manifest.fullTags());
        long lite = manifest.totalSize(Manifest.liteTags());
        long extreme = manifest.totalSize(Manifest.extremeTags());
        long onDisk = GameDir.size(GameDir.get(this));

        storageText.setText(
                "Full    : " + Manifest.humanSize(full) + "\n"
                        + "Lite    : " + Manifest.humanSize(lite)
                        + "  (hemat " + pct(lite, full) + ")\n"
                        + "Extreme : " + Manifest.humanSize(extreme)
                        + "  (hemat " + pct(extreme, full) + ")\n\n"
                        + "Terpasang sekarang: " + Manifest.humanSize(onDisk)
                        + "\n" + GameDir.describe(this));
    }

    private static String pct(long part, long whole) {
        if (whole <= 0) return "0%";
        return Math.round(100.0 * (whole - part) / whole) + "%";
    }

    // ----------------------------------------------------------------- pack ----

    private void showPackDialog() {
        if (manifest == null) { toast("Manifest belum siap"); return; }

        long full = manifest.totalSize(Manifest.fullTags());
        long lite = manifest.totalSize(Manifest.liteTags());
        long extreme = manifest.totalSize(Manifest.extremeTags());

        String rec = preset != null ? preset.recommendedTier : Manifest.TIER_FULL;
        String mark = "  <- disarankan";

        String[] items = {
                "Extreme — " + Manifest.humanSize(extreme)
                        + "\nmodels + data + suara umum, tanpa radio/speech/cutscene"
                        + (rec.equals(Manifest.TIER_EXTREME) ? mark : ""),
                "Lite — " + Manifest.humanSize(lite)
                        + "\nExtreme + radio (13 stasiun)"
                        + (rec.equals(Manifest.TIER_LITE) ? mark : ""),
                "Full — " + Manifest.humanSize(full)
                        + "\nsemua, termasuk speech NPC + cutscene"
                        + (rec.equals(Manifest.TIER_FULL) ? mark : "")
        };

        new UiDialog.Alert(this)
                .setTitle("Pilih paket data")
                .setItems(items, (d, which) -> {
                    String tier = which == 0 ? Manifest.TIER_EXTREME
                            : which == 1 ? Manifest.TIER_LITE : Manifest.TIER_FULL;
                    Prefs.setPack(this, tier);
                    confirmDownload(tier);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void confirmDownload(String tier) {
        if (preset != null && !preset.viable) {
            new UiDialog.Alert(this)
                    .setTitle("Perangkat di bawah batas")
                    .setMessage(DeviceProfile.verdict(spec, preset)
                            + "\n\nTetap unduh paket data?")
                    .setPositiveButton("Tetap", (d, w) -> startDownload(tier))
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }
        startDownload(tier);
    }

    private void startDownload(String tier) {
        if (AppConfig.packBaseUrl().isEmpty() && AppConfig.packArchiveUrl().isEmpty()) {
            toast("Isi PACK_BASE_URL atau PACK_ARCHIVE_URL di gradle.properties");
            return;
        }
        File dir = GameDir.get(this);
        long need = manifest == null ? 0 : manifest.totalSize(Manifest.tagsFor(tier));
        String warn = StorageTarget.checkSpace(dir, need);
        if (warn != null) {
            new UiDialog.Alert(this)
                    .setTitle("Ruang tidak cukup")
                    .setMessage(warn)
                    .setPositiveButton("Tutup", null)
                    .show();
            return;
        }
        progressHidden = false;
        InstallService.start(this, tier);
        ProgressBus.running(this, -1, "Menghubungi server…");
        ui.removeCallbacks(progressPoll);
        ui.post(progressPoll);
    }

    // -------------------------------------------------------------- imports ----

    private void pickZip(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, requestCode);
        } catch (Exception e) {
            toast("Tidak ada file manager: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (req == REQ_PACK_ZIP) {
            importPackFromZip(uri);
        } else if (req == REQ_MOD_ZIP) {
            importModFromZip(uri);
        }
    }

    private File copyToCache(Uri uri, String name) throws Exception {
        File out = new File(getCacheDir(), name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("tidak bisa baca file");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return out;
    }

    private void importPackFromZip(Uri uri) {
        Spinner pd = spinner("Mengimpor paket…");
        new Thread(() -> {
            try {
                File zip = copyToCache(uri, "pack.zip");
                Manifest m = manifest != null ? manifest : ManifestStore.load(this);
                EnumSet<Manifest.Tag> tags =
                        "full".equals(Prefs.pack(this)) ? Manifest.fullTags() : Manifest.liteTags();
                PackInstaller pi = new PackInstaller(m, tags, null);
                PackInstaller.Result r = pi.install(zip, GameDir.get(this), false);
                ui.post(() -> {
                    pd.dismiss();
                    refreshStorage();
                    toast("Selesai: " + r);
                });
            } catch (Exception e) {
                ui.post(() -> { pd.dismiss(); toast("Gagal impor: " + e.getMessage()); });
            }
        }, "import-pack").start();
    }

    private void importModFromZip(Uri uri) {
        Spinner pd = spinner("Memasang mod…");
        new Thread(() -> {
            try {
                File zip = copyToCache(uri, "mod.zip");
                File tmp = new File(getCacheDir(), "mod_unzip");
                ZipUtil.unzip(zip, tmp);
                List<File> files = new ArrayList<>();
                collect(tmp, files);
                ModInstaller mi = new ModInstaller(GameDir.get(this));
                List<ModInstaller.Installed> done =
                        mi.install(files, ZipUtil.modName(zip), true);
                Prefs.bumpMods(this);
                ui.post(() -> {
                    pd.dismiss();
                    StringBuilder sb = new StringBuilder();
                    for (ModInstaller.Installed i : done) sb.append("• ").append(i.target).append('\n');
                    new UiDialog.Alert(MainActivity.this)
                            .setTitle(done.size() + " file terpasang")
                            .setMessage(sb.length() > 0 ? sb.toString() : "Tidak ada file dikenali.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                ui.post(() -> { pd.dismiss(); toast("Gagal pasang mod: " + e.getMessage()); });
            }
        }, "import-mod").start();
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) collect(f, out);
            else out.add(f);
        }
    }

    // --------------------------------------------------------------- export ----

    private void exportPreset() {
        if (preset == null) { toast("Scan perangkat belum selesai"); return; }
        try {
            ContainerPreset cp = ContainerPreset.forDevice(AppConfig.appLabel(), preset,
                    GameDir.describe(this));

            String json = cp.toContainerJson(1);
            String args = cp.toSetupArgs(Prefs.nick(this), Prefs.server(this));
            String readme = "SAMP PC — preset container\n"
                    + "===========================\n\n"
                    + "Perangkat : " + spec.soc + " / " + spec.gpu + "\n"
                    + "Tier      : " + preset.tier + "\n"
                    + "Resolusi  : " + preset.screenSize + "\n"
                    + "Driver    : " + preset.graphicsDriver + " + " + preset.dxWrapper + "\n"
                    + "Target    : " + preset.fpsTarget + " fps\n\n"
                    + "Cara pakai di Winlator:\n"
                    + "1. Salin .container ke folder container Winlator (butuh root)\n"
                    + "   ATAU buat container baru lalu samakan nilainya manual.\n"
                    + "2. Jalankan setup-samp.sh (ada di repo) dengan argumen:\n"
                    + "   " + args + "\n";

            String a = ExportUtil.saveToDownloads(this, "samp-pc.container", json, "application/json");
            ExportUtil.saveToDownloads(this, "setup-args.txt", args, "text/plain");
            ExportUtil.saveToDownloads(this, "BACA-INI.txt", readme, "text/plain");

            new UiDialog.Alert(this)
                    .setTitle("Preset diekspor")
                    .setMessage("Disimpan di:\n" + a + "\n\n" + readme)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            toast("Gagal ekspor: " + e.getMessage());
        }
    }

    private void showStorageDialog() {
        List<String> items = StorageTarget.describeAll(this);
        String[] arr = items.toArray(new String[0]);
        StorageTarget.Mode cur = StorageTarget.currentMode(this);
        int checked = cur == StorageTarget.Mode.INTERNAL ? 0
                : cur == StorageTarget.Mode.PUBLIC ? 1 : 2;

        new UiDialog.Alert(this)
                .setTitle("Lokasi folder game")
                .setSingleChoiceItems(arr, checked, (d, which) -> {
                    StorageTarget.Mode mode = which == 0 ? StorageTarget.Mode.INTERNAL
                            : which == 1 ? StorageTarget.Mode.PUBLIC : StorageTarget.Mode.SDCARD;

                    if (mode != StorageTarget.Mode.INTERNAL && !StorageTarget.canWritePublic()) {
                        d.dismiss();
                        askAllFilesAccess(mode);
                        return;
                    }
                    if (mode == StorageTarget.Mode.SDCARD && StorageTarget.sdCardRoot() == null) {
                        toast("SD card tidak terdeteksi");
                        d.dismiss();
                        return;
                    }
                    StorageTarget.setMode(this, mode);
                    refreshStorage();
                    toast("Lokasi: " + GameDir.describe(this));
                    d.dismiss();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    /** Android 11+: minta izin "Akses semua file" untuk menulis ke storage publik/SD. */
    private void askAllFilesAccess(StorageTarget.Mode mode) {
        if (Build.VERSION.SDK_INT < 30) {
            StorageTarget.setMode(this, mode);
            refreshStorage();
            return;
        }
        new UiDialog.Alert(this)
                .setTitle("Butuh izin akses file")
                .setMessage("Untuk menulis ke storage publik/SD card, aktifkan "
                        + "\"Akses semua file\" untuk app ini.\n\nSetelah aktif, pilih lokasi lagi.")
                .setPositiveButton("Buka pengaturan", (d, w) -> {
                    try {
                        Intent i = new Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showGameFolder() {
        String path = GameDir.describe(this);
        long size = GameDir.size(GameDir.get(this));
        new UiDialog.Alert(this)
                .setTitle("Folder game")
                .setMessage(path + "\n\nUkuran: " + Manifest.humanSize(size))
                .setPositiveButton("Tutup", null)
                .show();
    }

    // --------------------------------------------------------------- launch ----

    /** Engine Winlator hanya ada untuk arm64-v8a (Box64 = ARM64). */
    private static boolean isArm64() {
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        return false;
    }

    private void launchGame() {
        if (!isArm64()) {
            new UiDialog.Alert(this)
                    .title("Perangkat ini bukan ARM64")
                    .message("Engine di APK ini hanya untuk arm64-v8a (HP ARM).\n\n"
                            + "ABI terdeteksi: " + android.text.TextUtils.join(", ", android.os.Build.SUPPORTED_ABIS)
                            + "\n\nEmulator x86 (MEmu/BlueStacks/LDPlayer) tidak bisa menjalankan "
                            + "Wine+Box64. Pakai HP ARM64 sungguhan, atau pakai APK frontend "
                            + "saja untuk lihat UI-nya.")
                    .positive("Mengerti", null)
                    .show();
            return;
        }

        // ---- APK gabungan: engine ada di dalam app ini sendiri ----------
        if (EngineBridge.enginePresent()) {
            if (preset == null) { toast("Scan perangkat belum selesai"); return; }
            ContainerPreset cp = ContainerPreset.forDevice(
                    AppConfig.appLabel(), preset, GameDir.describe(this));
            String err = EngineBridge.play(this, GameDir.get(this), cp);
            if (err != null) {
                new UiDialog.Alert(this)
                        .setTitle("Tidak bisa jalan")
                        .setMessage(err)
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

        // ---- mode lama: engine Winlator terpisah ------------------------
        String[] pkgs = {
                "com.sampwrap.winlator", "com.winlator",
                "com.winlator.cmod", "com.winlator.banner", "com.winlator.ludashi"
        };
        for (String pkg : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                startActivity(i);
                return;
            }
        }
        File game = new File(GameDir.get(this), "samp.exe");
        if (!game.exists()) {
            toast("samp.exe belum ada — unduh paket data dulu");
            return;
        }
        new UiDialog.Alert(this)
                .setTitle("Engine belum terpasang")
                .setMessage("Folder game sudah siap di:\n" + GameDir.describe(this)
                        + "\n\nBuka lewat Winlator (atau build APK gabungan, lihat README), "
                        + "lalu jalankan samp.exe dari folder itu.")
                .setPositiveButton("OK", null)
                .show();
    }

    // -------------------------------------------------------------- helpers ----

    private Spinner spinner(String msg) {
        Spinner pd = new Spinner(this, msg);
        pd.setMessage(msg);
        pd.setCancelable(false);
        pd.show();
        return pd;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }
}
