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


## 8. v1.4.0 — UI sendiri, progress bar, anti force-close

Keluhan yang masuk: *"ui nya jangan ada bawaan android, pas download data kok
gada progres bar nya, pas download selesai kok app nya force close, dan gua tes
di MEmu"*.

| Masalah | Penyebab | Perbaikan |
|---|---|---|
| tidak ada progress bar | `MainActivity` tidak pernah me-refresh; service cuma update notifikasi | `ProgressBus` (file `files/progress.txt`) + polling UI tiap 400 ms → dialog progress sendiri |
| force close saat download selesai | ekstraksi native jalan di proses app yang sama; SIGSEGV unrar = seluruh app mati | service dipindah ke `android:process=":sync"`; crash ekstraksi cuma mematikan proses itu |
| UI bawaan Android | `AlertDialog` + `ProgressDialog` | `UiDialog` (Alert/Progress/Bar) + `Spinner`, semua view buatan sendiri |
| app menggantung | proses `:sync` mati tanpa jejak | heartbeat 3 dtk di service, UI menganggap gagal kalau progress basi >25 dtk |
| penyebab tidak diketahui | tidak ada log | `CrashGuard` → `files/last_crash.txt`, bisa dibuka dari dialog |

Tambahan: ikon sendiri + nama **SAMP PC**, launcher lama Winlator dihapus
(1 ikon saja), cek ABI di tombol "Jalankan".

**Rilis:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.4.0>  
`500.793.659 B` · sha256 `ae5053108b4add2c2763f91db5f09460ab2222cce142f55e0c825cd2d2a9c927` · arm64-v8a · minSdk 28

### Soal armeabi-v7a
APK v7a **tidak bisa dibuat dari build ini**: seluruh engine (Wine, Box64,
`libsampunrar.so`) di-compile arm64-v8a saja (`ndk.abiFilters 'arm64-v8a'`),
dan `splits.abi` ditolak AGP selama `abiFilters` aktif. v7a butuh engine
32-bit yang benar-benar lain (Wine 32-bit + **Box86**, bukan Box64) — itu porting
terpisah, bukan pemisahan APK. Emulator x86 seperti MEmu juga tidak bisa
menjalankan APK arm64 kecuali image-nya punya ARM translation.


## 9. v1.5.0 — XversSamp (nama + ikon)

Permintaan: *"Ganti juga icon app nama app Misal XversSamp"* — dan user
mengonfirmasi APK bisa di-install di MEmu; untuk 32-bit diputuskan fokus dulu
ke arm64.

| Item | Jadi |
|---|---|
| Nama app | **XversSamp** (`app.label` di `assets/app_config.properties`, `@string/app_name`, label manifest) |
| Ikon | huruf **X** oranye `#F0A020` + titik teal `#78C8BE`, latar gelap `#1B2230→#14181F` |
| Adaptive icon | `mipmap-anydpi-v26/ic_launcher{,_round}.xml` → `@color/ic_launcher_bg` + `@mipmap/ic_launcher_fore` |
| Sumber ikon | `branding/res/mipmap-*` (di-generate dari `branding/icon_512.png`), di-copy workflow ke res fork |
| Launcher | tetap 1 (ikon Winlator dihapus total) |

**Rilis:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.5.0>  
`500.797.883 B` · sha256 `255aea2bb9d7a5de58a9413ed326dff696ba11d82c8696c9ac545a1007d7dbec` · arm64-v8a


## 10. v1.6.0 — kenapa "gagal mengunduh" & bersih dari nama Winlator

Laporan: *"gada progres bar gagal mengunduh katanya + intinya gabisa lah tolong ya lah
dan masih ada nama winlator dll"*.

### Akar masalah "gagal mengunduh"

| # | Penyebab | Perbaikan |
|---|---|---|
| 1 | **Service di proses `:sync` membaca `SharedPreferences`** — prefs tidak sinkron antar-proses. Link Drive pilihan user bisa terbaca kosong → service gagal sebelum menulis progress apa pun. | URL + mode penyimpanan dikirim lewat **Intent** (`EXTRA_URL`, `EXTRA_MODE`), bukan prefs. |
| 2 | Tidak ada retry: satu timeout/putus = gagal total. | **6x percobaan** + resume `Range: bytes=N-`; lanjut dari byte terakhir. |
| 3 | `checkSpace` memakai ukuran hasil ekstrak sebelum ukuran asli diketahui → sering "ruang tidak cukup" palsu. | Cek setelah `Content-Length` sebenarnya diterima; pesan menyebut angka nyata. |
| 4 | `pct = -1` (total belum diketahui) membuat `Bar` tidak menggambar apa pun → bar terlihat kosong. | `Bar` sekarang punya animasi **indeterminate** (segmen berjalan). |

### Diagnostik
Menu **Diagnostik** menampilkan: versi app, package, ABI, HP/Android, folder game, mode,
ruang bebas, ukuran terpasang, URL sumber, **hasil tes HTTP ke server** (code, tipe, ukuran, waktu),
`install.log` terakhir, dan crash terakhir. Ada tombol **Salin**.

Menu **Ganti link paket** → user bisa tempel link Drive salinannya sendiri kalau file
bawaan kena limit kuota Google (`Prefs.packUrl` / `Prefs.effectivePackUrl`).

### Nama "Winlator"
Workflow menambal resource engine: text node + nilai atribut di `res/**/*.xml`, dan
**hanya string literal** di `app/src/main/java/**` (nama class/package tidak disentuh
supaya JNI/refleksi aman). Sisa kata "Winlator" di APK hanya nama resource internal
(`BTChooseWinlatorPath`, dst.) dan nama class — tidak tampil di UI.

### Repo public
`xykal/samp-pc` diubah dari private → **public** (tanpa rahasia di tree maupun riwayat;
sudah dipindai `ghp_`/`github_pat_`/`GITHUB_TOKEN=`).

**Rilis:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.6.0>  
`500.809.555 B` · sha256 `65945de3c27f62f17b2119f69369bacbd4f920ce5a709d68f64e93388a79eee2` · arm64-v8a


## 11. v1.7.0 — bug DONE di tahap antara (progres hilang) 

Laporan user (Diagnostik v1.6.0): unduhan **berhasil** 712.800.749 B dalam 72 dtk,
HTTP 200, tipe `application/octet-stream` — tapi "progres di app ngga ada",
"terpasang 0 B", dan setelah selesai tidak terjadi apa-apa.

**Akar masalah:** `postProgress(text, idx, total, done=true)` dipakai untuk
tahap antara ("Mengunduh paket…", "Membuka RAR dalam…", "Memasang SA-MP…").
Tiap panggilan menulis `ProgressBus.done()`; `MainActivity` melihat DONE →
tampilkan "Paket siap" → `ProgressBus.idle()` → **berhenti polling**. Setelah
itu semua update RUNNING tidak pernah dibaca lagi. Itu juga sebabnya
"terpasang 0 B" (refreshStorage dipanggil terlalu dini) dan tidak ada kabar
setelah unduh.

**Perbaikan v1.7.0**

| Perubahan | Alasan |
|---|---|
| `done=true` hanya di akhir (2 tempat) | tahap antara tidak lagi menandai selesai |
| Kartu progress **inline** di layar utama | dialog bisa ketutup/terlewat; kartu tidak |
| `alertState` guard | dialog "Paket siap"/"Gagal" tidak dobel |
| Log rinci ekstraksi (`RarNative tersedia`, pass 1, rar dalam, pass 2, ukuran, isi folder) | tahu persis di tahap mana berhenti |
| Pagar `samp.exe` setelah ekstrak | tidak ada lagi "selesai" palsu |
| Diagnostik: `RAR`, `samp.exe`, `ISI` | langsung kelihatan kalau lib unrar gagal dimuat |
| Banner x86 + dialog "data belum ada" di tombol Jalankan | user di MEmu paham batasnya |

**Rilis:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.7.0>  
`500.813.447 B` · sha256 `d578bc14c0929ab7264072d76338d917f009d31311e1715194b34c4fdf1ddc6c`


## 12. v1.8.0 — Drive 403, impor RAR, dan pengujian end-to-end

Keluhan: *"gagal bre intinya kayak Drive gagal 403 ... kalo bisa lu langsung tes sendiri sebelum
ngasih gua validasi"*.

### Pengujian mandiri (sandbox, kode yang sama dengan APK)

| Uji | Hasil |
|---|---|
| `DriveDownloader.open()` penuh | HTTP 200, `application/octet-stream`, **712.800.749 B dalam 6 dtk** |
| resume `open(url, 64 MB)` | **HTTP 206**, `Content-Range: bytes 67108864-712800748/712800749` |
| unrar (48 unit source = list CMake JNI) pass 1 | RAR luar → `TUTOR GTA SAMP PC/GTA SAN ANDREAS FIXED.rar` (612.599.927 B) |
| isi arsip dalam | 448 entri, root `GTA SAN ANDREAS FIXED/`, ada `gta_sa.exe`, **tidak ada** `samp.exe` |
| manifest vs arsip | 412/412 path manifest ada (5 sisanya `samp-install/*` yang memang dilewati) |
| ekstrak LITE | **2.4 GB** (`unrar x @filter_lite.txt`) |
| tempel `samp-client.zip` | **`samp.exe` + `gta_sa.exe` ADA**, `models/gta3.img`, `audio/streams/AA` ada |

Kesimpulan: pipeline data (unduh → RAR berlapis → tier → client) **terbukti benar**;
kegagalan user murni di sisi **Drive (403)**.

### Perubahan
- `DriveDownloader`: `CookieManager(ACCEPT_ALL)`, 3 endpoint berurutan
  (`drive.usercontent.google.com` → `drive.google.com/uc` → `docs.google.com/uc`),
  pesan khusus 403 (kuota) / 404 / 429, ringkasan HTML Google di pesan error.
- Arsip **tidak dihapus** sebelum pemasangan sukses → retry memakai `Range` (resume).
- `InstallService.startLocal()` + `ACTION_LOCAL`: impor **ZIP maupun RAR** lewat
  pipeline yang sama (progress, verifikasi `samp.exe`, log).
- Dialog gagal: **Diagnostik / Impor file / Coba lagi**.

**Rilis:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.8.0>  
`500.815.867 B` · sha256 `6222961776d0c26e3f857a28357dd5e54477e1a26c1e4a16f19d60c5fad6a911`


## 13. v1.8.1 — audit APK: 3 URL rusak karena rebranding

Audit isi APK v1.8.0 (bukan asumsi) menemukan penggantian kata "Winlator" ikut
menyentuh **URL**:

| Sebelum | Sesudah (rusak) |
|---|---|
| `raw.githubusercontent.com/StevenMXZ/Winlator-Contents/main/contents.json` | `.../XversSamp-Contents/...` |
| `github.com/StevenMXZ/Winlator-Contents/releases/download/1.0/Proton.9.0-x86_64.wcp` | `.../XversSamp-Contents/...` |
| `.../proton-10-arm64ec.wcp.xz` | idem |

Efek: fitur unduh konten tambahan (Wine/DXVK versi lain) 404.
Perbaikan: string literal yang mengandung `http://`, `https://`, `github.com`,
`githubusercontent` tidak lagi disentuh. Audit APK v1.8.1: **URL rusak = 0**.

Catatan: `package com.winlator.cmod` **tidak boleh** diganti — JNI & refleksi
memanggil class lewat package itu. Yang bisa diganti hanya teks yang tampil.

## 14. Analisis "engine sendiri"

Lihat `docs/ENGINE-SENDIRI.md`. Ringkas, dengan angka ukur APK v1.8.0:

| Komponen | Ukuran |
|---|---|
| `imagefs.tar.zst` (rootfs Linux + X server + PulseAudio) | 184,5 MB |
| `proton-9.0-arm64ec.tar.zst` ← **ini Wine-nya** | 70,2 MB |
| pattern proton (arm64ec + x86_64) | 18,1 MB |
| `container_pattern_common.tzst` | 38,6 MB |
| `dxwrapper/*` 5 varian DXVK | 31,3 MB |
| `graphics_driver/*` 5 varian | 38,3 MB |
| `wincomponents/direct3d.tzst` | 29,8 MB |
| `box64-0.4.2.tzst` | 3,8 MB |
| `lib/arm64-v8a/*.so` | 10,9 MB |

Tiga opsi: **(A) engine ramping buatan kita** (±425–450 MB, 1–2 hari, risiko sedang),
**(B) ganti Wine dengan build sendiri** (mingguan–bulanan, butuh perangkat ARM64 untuk uji),
**(C) dari nol** (tidak realistis; dan tidak menyelesaikan MEmu x86).

**Rilis v1.8.1:** <https://github.com/xykal/samp-pc-download/releases/tag/v1.8.1>  
`500.815.863 B` · sha256 `d483d5125604558ef6ed7a7f02d1c54f9a644a440d24e8709a852f0ef04fba18`


## 15. Engine sendiri (Wine) — pabrik build sudah jalan

User memilih Opsi B: **Wine build sendiri**. Progres nyata:

| Langkah | Hasil |
|---|---|
| Workflow `build-wine.yml` di runner **ARM64 native** (`ubuntu-24.04-arm`) | ✅ jalan |
| Wine 9.0 dari source resmi, `--enable-archs=i386` | ✅ configure + compile |
| PE builtins `i386-windows` (786 DLL) + `aarch64-unix` (.so) | ✅ |
| Durasi | ±12 menit |
| Artefak | `proton-ours.tar.zst` 132 MB (636 MB isi) |
| Build #1 | ❌ llvm-mingw salah pakai (`libgcc.a` tidak ketemu) → diperbaiki di #2 |

**Temuan penting:** `ntdll.so` bawaan APK adalah
`ELF 64-bit ARM aarch64, dynamically linked, **for Android**` → layer Proton
di-compile dengan **NDK/bionic**, bukan glibc. Artefak glibc kita karena itu
belum bisa dipasang; target build harus `aarch64-linux-android28`.

Resep berikutnya (dari dokumentasi builder publik + bedah APK):
- Tahap 0: host gcc → native tools (wrc/winebuild/widl) + NLS
- Tahap 1: NDK clang → `aarch64-linux-android28` (unix side), llvm-mingw i686 → PE `i386-windows`
- Paket: tar+zstd dengan struktur `bin/ lib/wine/ share/wine/`

NDK hanya tersedia untuk host **x86_64**, jadi build NDK pindah ke runner
`ubuntu-24.04` (cross-compile tetap ke aarch64). Patch bionic adalah bagian
yang paling banyak iterasi.

Detail: `docs/WINE-SENDIRI.md`. Engine Proton yang sekarang **tidak disentuh**
supaya app tetap bisa dipakai.

## 16. v1.9.0 — engine sendiri sudah masuk APK (2026-10-22)

Ringkas hasil lanjutan dari §15:

| Langkah | Hasil |
|---|---|
| Workflow bionic `build-wine-android.yml` (ID 392212432) | ✅ run 39701770654 sukses |
| Wine 10.17, host `aarch64-linux-android28`, PE i386, `winex11.drv` | ✅ |
| Artefak layer | v2 32.794.360 B (31,3 MiB), sha256 `2ab2c2d2…ecb6` (v1 tanpa program inti: 32.514.687 B) |
| APK v1.9.1 | 460.142.079 B (turun 40,7 MB dari v1.8.1) |
| Aset di APK | `imagefs.tar.zst` 193.425.249 B (tetap) + `proton-9.0-arm64ec.tar.zst` = **layer kita** 32.514.687 B |
| Verifikasi | ELF ARM64 bionic (`linker64`), PE i386, isi `bin/lib/share` lengkap, APK signed, nama app XversSamp, `libsampunrar.so` ada |
| Uji di HP ARM64 | ❌ belum — tidak ada perangkat di sandbox |

Iterasi yang dilalui (semua sudah diperbaiki, detail di `docs/WINE-SENDIRI.md`):
multilib host → FreeType di stage 0 → `wineandroid.drv` → `regparm` (header glibc)
→ `SONAME_LIBX11` → `-lX11` (LDFLAGS + symlink imagefs) → `XF86VidMode` →
path install `arm64-v8a` → task `downloadProton` di `app/build.gradle` yang
menghapus layer kita dan mengunduh stock.

Konsekuensi: layer Wine bawaan (70,2 MB) tidak dipakai lagi; kalau runtime gagal,
v1.8.1 tetap tersedia sebagai pembanding.

### Koreksi v1.9.0 → v1.9.1

v1.9.0 kehilangan 14 program PE karena `configure.ac` meng-default
`enable_{conhost,plugplay,rpcss,services,spoolsv,termsv,wineboot,winemenubuilder,wuauserv}`
ke `$HOST_ARCH` (aarch64) sedangkan PE yang dibangun hanya i386. Setelah
`--enable-<program>=i386`, jumlah EXE naik 100 → 109 dan `wineboot.exe`,
`services.exe`, `rpcss.exe` ada. v1.9.0 ditandai *prerelease* dan digantikan v1.9.1.
