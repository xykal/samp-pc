# STATUS — SAMP PC (bungkus jadi satu)

Terakhir diperbarui: 2026-10-21

## 1. Yang sudah jadi

| Komponen | Status | Keterangan |
|---|---|---|
| APK gabungan (engine + frontend) | ✅ **build sukses** | 1 ikon, tanpa UI emulator |
| APK frontend saja (ringan) | ✅ build sukses | 74 KB, buat tes cepat |
| Tombol **Jalankan SA-MP** → engine internal | ✅ | `EngineBridge` (reflection, tanpa UI Winlator) |
| Unduh paket dari **link Google Drive** | ✅ | handle halaman "scan virus" Drive |
| Unduh per-file + resume | ✅ | mode manifest TSV |
| Ekstrak ZIP otomatis ke SD / internal | ✅ | `PackInstaller` |
| 3 tier data (extreme/lite/full) | ✅ | 1,55 / 2,44 / 4,71 GB |
| Deteksi GPU & preset otomatis | ✅ | Adreno/Mali/PowerVR → tier |
| Kontrol sentuh kustom | ✅ | `control_layout.json` + `ControlsActivity` |
| CI build via GitHub Actions | ✅ | standalone + merged |
| **Ekstrak RAR5 di HP** (UnRAR JNI) | ✅ | `libsampunrar.so` — tanpa langkah PC |
| Client SA-MP otomatis | ✅ | diunduh dari aset rilis (24 MB) |
| Konversi RAR → ZIP di PC | ⚪ opsional | `tools/prepare-packs.bat`, tak perlu lagi |

## 2. Link

- Repo: https://github.com/xykal/samp-pc
- **APK gabungan v1.3.0 (R8, 501 MB — install ini):**
  https://github.com/xykal/samp-pc-download/releases/download/v1.3.0/samp-pc-v1.3.0-merged.apk
- Cadangan kalau v1.3.0 bermasalah (debug, tanpa R8, 572 MB):
  https://github.com/xykal/samp-pc-download/releases/download/v1.2.0/samp-pc-v1.2.0-merged-debug.apk
- APK frontend saja:
  https://github.com/xykal/samp-pc-download/releases/download/v1.1.0/samp-pc-v1.1.0-frontend-debug.apk
- Halaman unduhan: https://github.com/xykal/samp-pc-download/releases/latest
- Panduan PC (RAR → ZIP → Drive): `PANDUAN-PC.md`
- Panduan lama (arsitektur): `PANDUAN.md`, `docs/ONE-APK.md`

## 3. Cara pakai (paling singkat)

1. Install APK v1.2.0 di HP.
2. Buka app → pilih penyimpanan → pilih tier → **Unduh paket**.
3. Tunggu → **Jalankan SA-MP**.

Tidak ada langkah PC, tidak ada link yang ditempel. Link paket (Drive) dan
client SA-MP sudah ditanam di dalam APK (`assets/app_config.properties`).

## 4. Isi APK gabungan (terverifikasi)

- `com.sampwrap.*` (frontend, 18 class) ✅ ada di `classes12.dex` / `classes13.dex`
- `com.winlator.cmod.*` (engine) ✅
- `lib/arm64-v8a/*.so` — `libwinlator.so`, `libfakeinput.so`, adrenotools ✅
- `assets/app_config.properties`, `manifest_default.tsv`, `control_layout.json` ✅
- `assets/imagefs.tar.zst` (193 MB) + `proton-9.0-arm64ec` (74 MB) ✅
- Manifest: **1 launcher** (`com.sampwrap.app.MainActivity`), 2 activity + 1 service tambahan ✅
- Ukuran: **571.961.861 byte** (546 MB), debug-signed, arm64-v8a
- sha256: `11a0b0ad77b637eb975d360ee125502686dc6dc4ad038e83faa52fbc0059c80c`

## 5. Catatan jujur

- **Helio G36**: GTA:SA PC lewat Box64 realitisnya 5–12 fps di 640×360 SAFETY.
  Bisa dimainkan buat nostalgia, bukan buat war server. Turunkan tier ke EXTREME
  dan tutup app lain.
- **Debug signing**: kalau nanti mau rilis ke Play Store, perlu signing key sendiri
  (workflow tinggal ditambah `signingConfig`).
- **Belum ada**: auto-update APK, cloud save, anti-cheat server.

## 6. Langkah berikut (kalau mau lanjut)

1. Ganti `app.label` + `server.default` di `assets/app_config.properties`.
2. Upload ZIP ke Drive → isi `pack.archiveUrl`.
3. (Opsional) host sendiri pakai Cloudflare R2 + `make_manifest.py` untuk mode per-file.


## 7. Ukuran APK — R8 & ABI

| | debug (v1.2.0) | R8 (v1.3.0) |
|---|---|---|
| APK | 572 MB | **501 MB** |
| dex (kode) | 76,4 MB | **6,5 MB** |
| waktu build CI | ±40 menit | ±9 menit |

R8 dipasang di `release` (`minifyEnabled true`, `shrinkResources true`).
Engine `com.winlator.**` **di-keep utuh** (tidak di-obfuscate) karena dipanggil
JNI + refleksi; yang menyusut library Compose/AndroidX/Kotlin yang tak terpakai.

**Soal split per-ABI:** tidak bisa dan tidak berguna di build ini —
`build.gradle` fork sudah memakai `ndk.abiFilters 'arm64-v8a'`, dan AGP
menolak `splits.abi` saat keduanya aktif
(`Conflicting configuration ... in ndk abiFilters cannot be present when splits abi filters are set`).
Engine memang hanya menghasilkan arm64-v8a, jadi tidak ada ABI lain untuk dipisah.

**Sisa berat ada di assets, bukan kode:**

| Komponen | Ukuran |
|---|---|
| `imagefs.tar.zst` | 193 MB |
| proton (arm64ec + pattern) | 92 MB |
| graphics driver (beberapa varian) | 46 MB |
| dxwrapper (beberapa versi DXVK) | 46 MB |
| container pattern | 40 MB |
| wincomponents | 38 MB |

Kalau mau jauh lebih kecil, opsinya: pindahkan imagefs/proton/pattern ke
unduhan saat pertama buka (hemat ±325 MB), atau kirim hanya driver+DXVK yang
dipakai device (hemat ±90 MB). Keduanya menyentuh alur engine, belum dikerjakan.
