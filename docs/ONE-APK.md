# Bungkus jadi SATU APK (gabung ke fork Winlator) + kontrol custom + storage

Dokumen ini melanjutkan `app/README.md`. Hasil akhirnya: **satu APK** yang
dibuka → scan GPU → pilih paket → unduh & ekstrak → PLAY, tanpa user pernah
melihat desktop Wine.

---

## Status yang sudah jadi (dan yang belum)

| Bagian | Status |
|---|---|
| Core: manifest asli dari paket Drive (417 file, 4,71 GB) | ✅ jadi, angka nyata |
| 3 tier ukuran: Extreme 1,55 GB / Lite 2,44 GB / Full 4,71 GB | ✅ jadi |
| Download di app: per-file (resume + sha256) atau 1 ZIP | ✅ jadi |
| Ekstrak otomatis di app sesuai tier | ✅ jadi (ZIP) |
| Storage: internal / publik / SD card | ✅ jadi (`StorageTarget`) |
| Preset GPU: 5 tier termasuk Helio G36 | ✅ jadi + test |
| Kontrol custom (JSON + editor drag) | ✅ jadi (`TouchControls`, `ControlsActivity`) |
| Jembatan kontrol ke engine Winlator | ⚠️ kerangka (`WinlatorInputSink`, refleksi) — perlu 1x verifikasi nama method di fork |
| **APK gabungan (engine di dalam APK)** | ⚠️ langkah-langkah di bawah; perlu dikerjakan sekali di fork |

Artinya: **semua logika app-nya siap dan ter-compile**. Yang belum adalah
menggabungkan source app ini ke source Winlator (pekerjaan file-copy + 6 patch
titik), karena repo fork-nya besar dan nama class-nya bisa berubah antar versi.

---

## Langkah gabung (sekali jalan, ±2–3 jam)

### 1. Ambil fork

```bash
git clone https://github.com/StevenMXZ/Winlator-Ludashi.git winlator-samp
cd winlator-samp
git checkout -b samp-pc
```

> Alternatif: `The412Banner/Bannerlator` (GPL-3.0, fitur lebih banyak) atau
> `Pipetto-crypto/Winlator-Bionic`.

### 2. Tempel kode app ini

```bash
# dari repo SAMP PC
cp -r app/app/src/main/java/com/sampwrap        winlator-samp/app/src/main/java/
cp -r app/app/src/main/assets/control_layout.json winlator-samp/app/src/main/assets/
cp -r app/app/src/main/assets/manifest_default.tsv winlator-samp/app/src/main/assets/
```

Ubah `BuildConfig` di app ini menjadi konstanta biasa, atau tambahkan field
yang sama di `app/build.gradle` fork:

```groovy
buildConfigField "String", "APP_LABEL",        "\"SAMP PC\""
buildConfigField "String", "DEFAULT_SERVER",   "\"play.domainkamu.com:7777\""
buildConfigField "String", "PACK_BASE_URL",    "\"https://cdn.domainkamu.com/samp\""
buildConfigField "String", "PACK_ARCHIVE_URL", "\"\""
```

### 3. Jadikan launcher

Di `app/src/main/AndroidManifest.xml` fork:

```xml
<activity android:name="com.sampwrap.app.MainActivity"
          android:exported="true"
          android:screenOrientation="portrait">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
</activity>
```

Hapus intent-filter LAUNCHER dari `MainActivity` lama fork (biar cuma satu
ikon). Activity `XServerDisplayActivity` biarkan.

### 4. Folder game → drive `D:` container

`StorageTarget` menghasilkan path nyata di storage. Di fork, saat membuat
container:

```java
// com.winlator.container.Container
File gameDir = com.sampwrap.app.GameDir.get(context);   // internal/publik/SD
container.setDrives("D:" + gameDir.getAbsolutePath());
```

Di dalam container, folder itu muncul sebagai `D:\`.

### 5. Container otomatis dari preset GPU

Setelah container dibuat (sekali di first run):

```java
DeviceProfile.Spec spec = DeviceScan.spec(this);
DeviceProfile.Preset preset = DeviceProfile.pick(spec);
ContainerPreset cp = ContainerPreset.forDevice("SAMP PC", preset, gameDir.getAbsolutePath());

// tulis .container sesuai format fork (cek Container.java di fork)
File cfg = new File(container.getRootDir(), ".container");
FileUtils.writeString(cfg, cp.toContainerJson(container.getId()));

container.setScreenSize(preset.screenSize);
container.setGraphicsDriver(preset.graphicsDriver);
container.setDXWrapper(preset.dxWrapper);
container.setEnvVars(cp.envVars);
```

### 6. Tombol PLAY → langsung `samp.exe`

Di fork, cari pembuatan `Shortcut` (biasanya `com.winlator.core.Shortcut` +
`ShortcutManager`). Tiru polanya:

```java
Shortcut s = new Shortcut(container, new File(gameDir, "samp.exe"));
s.setName("SA-MP PC");
s.setExtra("execArgs", "");
ShortcutManager.editShortcut(context, s);   // atau addShortcut
// lalu:
Intent i = new Intent(this, XServerDisplayActivity.class);
i.putExtra("container_id", container.getId());
i.putExtra("shortcut_path", s.getFile().getAbsolutePath()); // cek nama key di fork
startActivity(i);
```

`MainActivity.launchGame()` di app ini sudah mencari paket
`com.winlator*`; setelah digabung, ganti isi method itu dengan intent di atas.

### 7. Kontrol custom → engine

`TouchControls` mengirim event ke `Sink`. Setelah digabung, pasang di
`XServerDisplayActivity`:

```java
TouchControls tc = new TouchControls(this);
tc.setSink(new WinlatorInputSink(inputDevice));   // field inputDevice milik activity
// masukkan tc ke container view di atas XServerView
activityLayout.addView(tc, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
```

`WinlatorInputSink` memakai refleksi, jadi:
- kalau method `sendKeyEvent(KeyEvent)` / `mouseMove(float,float)` ada → langsung nyambung
- kalau namanya beda → cukup ubah 3 string di `find(...)`, tidak perlu ubah logika

Cek sekali di fork:
```bash
grep -rn "mouseMove\|sendKeyEvent\|sendMouseButton" app/src/main/java/com/winlator/inputcontrols/
```

Kalau pakai sistem InputControls bawaan fork (bukan overlay kita), layout di
`control_layout.json` masih berguna sebagai acuan tombolnya.

### 8. Build

```bash
./gradlew assembleDebug
# atau lewat Actions: .github/workflows/build-merged.yml (template disediakan)
```

---

## Yang perlu user siapkan (hosting data)

App butuh URL. Dua pilihan:

**A. Per-file (disarankan)** — folder data di-serve apa adanya (nginx/object storage):
```
https://cdn.domainkamu.com/samp/models/gta3.img
https://cdn.domainkamu.com/samp/audio/streams/CR
...
```
Set `sampwrap.packBaseUrl=https://cdn.domainkamu.com/samp`.
Manifest-nya sudah ada (412 entri, URL ter-encode otomatis). Resume + sha256
didukung.

**B. Satu ZIP per tier** — untuk hosting yang lebih simpel:
```bash
python3 tools/make_pack_zips.py --dir "/path/GTA SAN ANDREAS FIXED" --out packs
```
Set `sampwrap.packArchiveUrl=https://cdn.domainkamu.com/samp/pack-extreme.zip`.
App akan unduh → ekstrak otomatis dengan filter tier.

> Paket asli dari Drive itu **RAR**. App tidak membuka RAR (butuh library
> tambahan). Ekstrak dulu di PC (WinRAR/7-Zip) → pakai salah satu opsi di atas.

---

## Storage: internal vs SD

| Pilihan | Path | Izin |
|---|---|---|
| Internal app | `/storage/emulated/0/Android/data/com.sampwrap.app/files/Games/GTA-SA` | tidak perlu |
| Publik | `/storage/emulated/0/Games/GTA-SA` | "Akses semua file" |
| SD card | `/storage/XXXX-XXXX/Games/GTA-SA` | "Akses semua file" |

App cek ruang sebelum mulai (`StorageTarget.checkSpace`) dan menolak jalan kalau
kurang 128 MB dari kebutuhan. Untuk **Full 4,71 GB**, siapkan ≥ 5,5 GB kosong.

---

## Angka asli dari paket Drive kamu

| Tag | File | Ukuran | Bisa dibuang? |
|---|---|---|---|
| `speech` (SPC_*) | 5 | **1,81 GB** | ya — NPC jadi bisu, gameplay aman |
| `core` (models/data/anim) | 386 | 1,19 GB | tidak |
| `radio` | 13 | 0,89 GB | ya — atau ganti stream |
| `cutscene` | 2 | 0,38 GB | ya — hanya SP |
| `sfx` | 4 | 0,34 GB | tidak |
| `ads` (iklan/ambience) | 2 | 0,10 GB | ya |

Tier: **Extreme 1,55 GB** · **Lite 2,44 GB** · **Full 4,71 GB**.
