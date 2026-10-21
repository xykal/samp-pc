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
| Konversi RAR → ZIP di PC | ✅ | `tools/prepare-packs.bat` |

## 2. Link

- Repo: https://github.com/xykal/samp-pc
- **APK gabungan (install ini):**
  https://github.com/xykal/samp-pc/releases/download/v1.1.0-merged/samp-pc-v1.1.0-merged-debug.apk
- APK frontend saja:
  https://github.com/xykal/samp-pc/releases/download/v1.0.0/samp-pc-v1.0.0-debug.apk
- Panduan PC (RAR → ZIP → Drive): `PANDUAN-PC.md`
- Panduan lama (arsitektur): `PANDUAN.md`, `docs/ONE-APK.md`

## 3. Cara pakai (paling singkat)

1. `PANDUAN-PC.md` langkah 1–3 → dapat `pack-extreme.zip` + `samp-install.zip`.
2. Upload ke Drive, **Share → Anyone with the link**.
3. Install APK gabungan di HP.
4. Di app: tempel link Drive → Unduh paket → **Jalankan SA-MP**.

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
