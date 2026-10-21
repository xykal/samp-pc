# XversSamp v1.5.0 — nama & ikon baru

**Download (501 MB, arm64):**
https://github.com/xykal/samp-pc-download/releases/download/v1.5.0/xverssamp-v1.5.0-merged.apk

sha256: `255aea2bb9d7a5de58a9413ed326dff696ba11d82c8696c9ac545a1007d7dbec`

---

## 1. Nama: SAMP PC → **XversSamp** ✅

Diganti di semua tempat, bukan cuma di launcher:

- label ikon di home screen
- judul di dalam app
- judul dialog & progress
- judul notifikasi download

## 2. Ikon baru ✅

Huruf **X** oranye (`#F0A020`) + titik teal (`#78C8BE`) di atas latar gelap
gradasi. Ikon Winflator/Winlator yang lama sudah benar-benar hilang — termasuk
*adaptive icon* Android 8+, jadi di HP modern pun yang muncul ikon X ini.

Tetap cuma **1 ikon** di home screen (launcher bawaan engine sudah dihapus
sejak v1.4.0).

## 3. Yang tetap dari v1.4.0

- Progress bar **di dalam app** waktu download (`Mengunduh 42% (285/680 MB)`, `Mengekstrak… 1200 MB`).
- Service download di proses terpisah (`:sync`) → ekstraksi crash **tidak** menutup app, cuma muncul dialog "Gagal" + tombol **Lihat log**.
- Crash terakhir bisa dilihat dari dalam app (`CrashGuard`).
- Semua dialog buatan sendiri, bukan UI bawaan Android.

## 4. MEmu — makasih infonya 👍

Oke, berarti image MEmu kamu punya **ARM translation** (libhoudini), jadi APK
arm64-nya ke-install. Itu memang bisa. Yang perlu diingat:

- install ≠ jalan — engine-nya butuh eksekusi ARM64 asli;
- lewat translation, Box64 (penerjemah x86 Windows → ARM64) jadi dobel
  terjemahan, biasanya lambat banget atau gagal di grafis (Vulkan/GL driver);
- jadi kalau di MEmu nanti mentok di layar hitam / keluar sendiri, itu batas
  emulatornya, bukan APK-nya. Buat main beneran tetap pakai HP ARM64.

Kalau di MEmu kamu cuma mau cek tampilan baru (dialog, progress bar, ikon, nama),
itu valid dan berguna — silakan screenshot kalau ada yang aneh.

## 5. 32-bit: oke, fokus 64 dulu ✅

Betul, armeabi-v7a itu **beda engine**, bukan APK dipisah:

| | arm64 (sekarang) | armeabi-v7a (nanti) |
|---|---|---|
| Penerjemah | Box64 | Box86 |
| Wine | 64-bit | 32-bit |
| Status | jadi, jalan | belum ada, harus di-build dari nol |

Jadi sekarang semua kerjaan fokus ke arm64 dulu — sesuai keputusanmu.

## Cara update

Install langsung di atas v1.4.0 (tanda tangan sama, package sama), nggak perlu
uninstall. Data yang sudah terunduh tidak hilang.
