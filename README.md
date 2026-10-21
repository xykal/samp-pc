# SAMP PC — Wrapper APK

Menjalankan **SA-MP versi PC (x86)** di Android lewat Wine + Box64, dengan
frontend yang menyembunyikan emulator.

## ⬇️ APK siap pakai

**APK gabungan v1.8.1 (XversSamp):** <https://github.com/xykal/samp-pc-download/releases/download/v1.8.1/xverssamp-v1.8.1-merged.apk>
v1.8.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.8.0/xverssamp-v1.8.0-merged.apk>
v1.7.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.7.0/xverssamp-v1.7.0-merged.apk>
v1.6.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.6.0/xverssamp-v1.6.0-merged.apk>
v1.5.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.5.0/xverssamp-v1.5.0-merged.apk>
v1.4.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.4.0/samp-pc-v1.4.0-merged.apk>  
v1.3.0: <https://github.com/xykal/samp-pc-download/releases/download/v1.3.0/samp-pc-v1.3.0-merged.apk>
v1.3.0 sebelumnya: <https://github.com/xykal/samp-pc-download/releases/download/v1.3.0/samp-pc-v1.3.0-merged.apk>
— 546 MB, arm64-v8a, debug-signed. Tinggal install → Unduh paket → main.
Tidak ada langkah PC dan tidak ada link yang perlu ditempel.

**Terverifikasi:****Terverifikasi:**
- ✅ **build di GitHub Actions sukses** — standalone *dan* gabungan dengan fork `StevenMXZ/Winlator-Ludashi@4.1`
- ✅ semua Java **compile bersih** dengan `android.jar` API 34 (bukan cuma "kelihatan benar")
- ✅ 38 assertion test core lulus (`CoreTest`), termasuk parsing link Google Drive
- ✅ ekstraksi RAR5 asli diuji di PC: 395 file tier EXTREME → 390 file / 1,60 GB dalam 12 detik
- ✅ manifest data **asli** dari paket Drive kamu: 417 file, **4,71 GB**
- ✅ APK gabungan dicek isinya: `com.sampwrap.*` + `com.winlator.cmod.*` + `libwinlator.so`,
  manifest hanya punya **1 ikon LAUNCHER**

---

## 1. Isi

| Komponen | Fungsi |
|---|---|
| `MainActivity` | scan GPU/SoC/RAM → pilih tier → pilih paket → unduh → jalankan |
| `DeviceScan` | baca GPU asli via EGL, Vulkan, RAM, ARM64 |
| `DeviceProfile` | 5 tier preset (ULTRA … ULTRA_LOW untuk Helio G36) |
| `PackDownloader` | unduh per-file, **resume** (HTTP Range), verifikasi sha256 |
| `PackInstaller` | ekstrak ZIP sesuai tier + proteksi zip-slip |
| `InstallService` | unduh di background + progress notifikasi + cek ruang |
| `StorageTarget` | internal / storage publik / **SD card** |
| `ModInstaller` | `.asi` → root, `.cs` → CLEO/, `.txd/.dff` → modloader/<mod>/, auto-backup |
| `TouchControls` | overlay kontrol custom (JSON, drag-edit, scale) |
| `ControlsActivity` | layar atur kontrol (geser tombol, simpan) |
| `WinlatorInputSink` | jembatan event kontrol → engine (refleksi, siap dipasang) |
| `EngineBridge` | **APK gabungan**: tombol Play langsung ke engine internal lewat refleksi (UI emulator tidak muncul) |
| `DriveDownloader` | unduh ZIP dari link Google Drive "share" biasa (handle halaman konfirmasi Drive) |

---

## 2. Paket data (angka asli, bukan perkiraan)

Dari `TUTOR GTA SAMP PC.rar` (Drive):

| Tag | File | Ukuran |
|---|---|---|
| `speech` (SPC_*) | 5 | **1,81 GB** |
| `core` (models/data/anim/exe) | 386 | 1,19 GB |
| `radio` | 13 | 0,89 GB |
| `cutscene` | 2 | 0,38 GB |
| `sfx` | 4 | 0,34 GB |
| `ads` | 2 | 0,10 GB |

Tier di app:

| Tier | Ukuran | Isi |
|---|---|---|
| **Extreme** | **1,55 GB** | core + sfx (tanpa radio/speech/cutscene) |
| **Lite** | **2,44 GB** | Extreme + radio |
| **Full** | **4,71 GB** | semua |

Rincian lengkap: `docs/DATA-PACK.md`. Regenerate kapan saja:
```bash
python3 tools/make_manifest.py --dir "/path/GTA SAN ANDREAS FIXED" --write --sha256
```

---

## 3. Build di GitHub Actions

```bash
git init && git add . && git commit -m "SAMP PC wrapper"
git branch -M main
git remote add origin https://github.com/USERKAMU/samp-pc-wrapper.git
git push -u origin main
```

- **Standalone APK** (frontend saja, ±2–3 MB): workflow `build-apk.yml` otomatis.
- **APK gabungan** (frontend + engine Winlator): workflow `build-merged.yml`
  → jalankan manual, isi `fork_repo` (default `StevenMXZ/Winlator-Ludashi`).

Isi dulu `gradle.properties`:

```properties
sampwrap.appName=NamaKomunitas PC
sampwrap.server=play.domainkamu.com:7777
sampwrap.packBaseUrl=https://cdn.domainkamu.com/samp
# ATAU pakai satu arsip ZIP:
# sampwrap.packArchiveUrl=https://cdn.domainkamu.com/samp/pack-extreme.zip
```

---

## 4. Hosting data

**Mode A — per-file** (resume, hemat kuota):
serve folder game apa adanya di `PACK_BASE_URL`. Manifest 412 entri sudah
berisi URL lengkap.

**Mode B — satu ZIP per tier:**
```bash
python3 tools/make_pack_zips.py --dir "/path/GTA SAN ANDREAS FIXED" --out packs
# -> packs/pack-extreme.zip, pack-lite.zip, pack-full.zip
```

> Paket Drive kamu berbentuk **RAR**. App mengunduh & mengekstrak **ZIP**.
> Ekstrak RAR-nya sekali di PC, lalu pilih mode A atau B.

---

## 5. Kontrol custom

Layout default (bisa diedit di app → menu **Atur kontrol**):

- stick analog kiri (W/A/S/D)
- area geser kanan = kamera (mouse relatif)
- FIRE / AIM / JMP / RUN / F (kendaraan) / handbrake / horn
- T (chat) / ENTER / TAB (player list) / ESC / MAP
- tombol cepat perintah server (`/stats`, `/help`, `/kill`) — bisa diganti di
  `assets/control_layout.json`

Semua posisi dalam dp, tersimpan ke `filesDir/control_layout.json` setelah
diedit.

---

## 6. Batas realistis

| Perangkat | Tier | FPS jujur |
|---|---|---|
| Adreno 7xx/8xx, RAM 8 GB+ | ULTRA | 30 (locked) |
| Adreno 6xx, RAM 6–8 GB | PERFORMANCE | 30 |
| Mali G5x–G7x | BALANCED | 20–24 |
| PowerVR / tanpa Vulkan | SAFETY | 12–18 |
| **Helio G36 (A53 + PowerVR GE8320)** | **ULTRA_LOW** | **8–15, tidak realistis** |

App tetap jalan di G36 (data, mod, preset, unduh), tapi untuk **main** di
perangkat itu client native mobile / streaming masih jawaban yang benar.

---

## 7. Legal

- Fork Winlator = LGPL/GPL → source hasil gabungan wajib dipublikasi.
- Jangan bundle file GTA:SA ke dalam APK yang kamu sebar.
- Paket data yang kamu hosting adalah tanggung jawabmu (gunakan salinan yang kamu miliki).
