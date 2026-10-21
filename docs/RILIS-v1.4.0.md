# SAMP PC v1.4.0 — apa yang berubah

**Download (501 MB, arm64):**
https://github.com/xykal/samp-pc-download/releases/download/v1.4.0/samp-pc-v1.4.0-merged.apk

sha256: `ae5053108b4add2c2763f91db5f09460ab2222cce142f55e0c825cd2d2a9c927`

---

## 1. "UI-nya jangan ada bawaan Android" ✅

Sebelumnya app pakai `AlertDialog` dan `ProgressDialog` bawaan Android — itu
sumbernya kotak putih/abu-abu khas Android.

Sekarang semua dialog, progress, dan tombol digambar sendiri oleh app
(`UiDialog` + `Spinner`): latar gelap, kartu rounded, tombol pill oranye.
Tidak ada satu pun dialog sistem yang dipakai lagi.

Bonus: ikon launcher sendiri (huruf **S** oranye) dan nama app jadi **SAMP PC**.
Ikon Winlator yang lama sudah dihapus — di home screen cuma ada satu ikon.

## 2. "Pas download kok nggak ada progress bar" ✅

Dulu service download cuma update notifikasi, layar app-nya diam saja.

Sekarang:

- service menulis progress ke file kecil (`ProgressBus`),
- layar utama membacanya tiap **400 ms**,
- muncul dialog progress dengan **bar + persen + tahap**, misal:
  - `Mengunduh 42% (285/680 MB)`
  - `Mengekstrak… 1200 MB`
  - `Memasang SA-MP…`

Kalau mau main HP sambil nunggu, tekan **Sembunyikan** — unduhan tetap jalan
(tombolnya ada di notifikasi kalau mau balik).

## 3. "Pas download selesai kok app-nya force close" ✅

Penyebabnya: ekstraksi RAR jalan **di dalam proses app yang sama**. Begitu
lib unrar kena data yang bikin dia crash, seluruh app ikut mati — persis
setelah unduhan selesai (di situlah ekstraksi mulai).

Perbaikan:

1. **Service dipindah ke proses terpisah** (`android:process=":sync"`).
   Kalau ekstraksi meledak, yang mati cuma proses itu — layar app tetap hidup
   dan menampilkan **"Gagal" + tombol "Lihat log"**.
2. **Heartbeat 3 detik.** Kalau proses itu mati mendadak (crash native tidak
   bisa ditangkap Java), UI sadar dalam ~25 detik bahwa progress-nya basi, lalu
   bilang gagal — tidak menggantung selamanya di 87%.
3. **CrashGuard** menyimpan stack trace terakhir ke `files/last_crash.txt`,
   bisa dibuka dari dalam app. Kalau masih ada crash, log ini yang kita pakai
   buat cari penyebabnya — bukan tebak-tebakan lagi.
4. Semua error sekarang lewat `catch (Throwable)` dan muncul sebagai dialog,
   bukan app hilang sendiri.

## 4. Soal pisah APK armeabi-v7a & arm64 — jawaban jujur ❌

**Tidak bisa dipisah**, dan bukan karena malas:

- Engine di APK ini (Wine + Box64 + `libsampunrar.so`) di-compile **hanya untuk
  arm64-v8a** (`ndk.abiFilters 'arm64-v8a'` di build.gradle fork Winlator).
- `splits { abi ... }` langsung ditolak Gradle selama `abiFilters` aktif:
  `Conflicting configuration : 'arm64-v8a' in ndk abiFilters cannot be present when splits abi filters are set`.
- Lebih penting: **Box64 hanya ada untuk ARM64**. Di HP 32-bit yang benar-benar
  armeabi-v7a, engine ini secara teknis tidak bisa jalan — butuh **Box86 + Wine
  32-bit**, yaitu build engine yang benar-benar lain (porting terpisah), bukan
  pemisahan file APK.

Jadi yang dikirim tetap **satu APK arm64-v8a** (ini juga yang dipakai semua HP
Android sekarang — Helio G36 kamu pun ARM64).

Yang sudah ditambahkan: kalau APK dijalankan di perangkat non-ARM64, tombol
**Jalankan** sekarang menolak dengan pesan jelas ("Perangkat ini bukan ARM64",
plus daftar ABI yang terdeteksi) — bukan crash atau layar hitam.

## 5. Soal tes di MEmu ⚠️

MEmu itu emulator **x86/x86_64**. APK ini isinya kode ARM64, jadi:

- Tanpa ARM translation di image MEmu → install gagal / "App not installed".
- Dengan ARM translation pun Box64 (penerjemah x86 Windows → ARM64) tidak
  matching arsitektur host, jadi game tidak akan jalan normal.

Untuk tes nyata pakai **HP ARM64**. Kalau kamu cuma mau lihat UI barunya
(dialog, progress bar, ikon) di MEmu, itu tetap bisa kelihatan asal APK-nya
mau ke-install — tapi menjalankan GTA-nya tidak.

---

## Cara tes v1.4.0 di HP

1. Uninstall versi lama dulu (biar bersih).
2. Install `samp-pc-v1.4.0-merged.apk`.
3. Buka app → pilih penyimpanan (SD/internal) → pilih tier (mulai dari **LITE**).
4. Tekan **Unduh paket** → progress bar harus langsung kelihatan.
5. Tunggu sampai selesai → muncul dialog **"Paket siap"** (bukan app hilang).
6. Tekan **Jalankan SA-MP**.

Kalau di tengah jalan gagal: dialog **"Gagal mengunduh"** akan muncul dengan
tombol **Lihat log** — kirim isi log itu, dari situ ketahuan persis penyebabnya.

## Kalau masih ada yang aneh

Buka app lagi setelah crash: kalau ada file crash, app otomatis menampilkan
dialog **"App terakhir crash"** berisi stack trace + info HP + ABI. Screenshot
dialog itu cukup untuk saya lanjutin.
