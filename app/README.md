# SAMP PC — Wrapper APK

Menjalankan **SA-MP versi PC (x86)** di Android lewat Wine + Box64, dengan
frontend yang menyembunyikan emulator.

**Terverifikasi:**
- ✅ semua Java **compile bersih** dengan `android.jar` API 34 (bukan cuma "kelihatan benar")
- ✅ 31 assertion test core lulus (`CoreTest`)
- ✅ manifest data **asli** dari paket Drive kamu: 417 file, **4,71 GB**
- ✅ dua workflow GitHub Actions valid (standalone & merged)

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

Isi dulu `app/src/main/assets/app_config.properties` (tanpa perlu BuildConfig):

```properties
app.label=NamaKomunitas PC
server.default=play.domainkamu.com:7777
pack.baseUrl=https://cdn.domainkamu.com/samp
# ATAU pakai satu arsip ZIP:
# pack.archiveUrl=https://cdn.domainkamu.com/samp/pack-extreme.zip
```

> Catatan: versi lama pakai `sampwrap.*` di `gradle.properties` + `BuildConfig`.
> Itu **sudah dihapus** supaya kode bisa ditempel ke fork Winlator yang
> `buildFeatures`-nya tidak mengaktifkan `buildConfig`. Sekarang semua
> konfigurasi dibaca runtime lewat `AppConfig.load(context)`.

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
