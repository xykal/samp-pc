# Rilis v1.9.3 — data nggak lagi bergantung Drive + Diagnostik yang bisa dibaca

Tanggal: 2026-10-22

## Keluhan yang dijawab

> "data nya aja gada di drive … + error trus … pas didiagnosa juga malah [error]"

Dua hal terpisah, dua-duanya diperbaiki.

## 1. Sumber data: cermin sendiri, Drive jadi cadangan

Penyebab paling sering "data nggak ada": link Drive-nya **hidup**, tapi
unduhan diblokir kuota Google (403) atau kena 416 — dan kalau unduhan gagal di
tengah, folder game tetap kosong. Jadi paketnya sekarang **di-host sendiri**:

- utama: `github.com/xykal/samp-pc-download/releases/download/data-v1/xverssamp-data-pack-full.rar`
  (712.800.749 B, sha256 `cd2f8524f3520c21c3a0cf4f6939345e70a9d713b4cdff7252850dfa3b3e3c75`
  — **sama persis** dengan file Drive, sudah dicek GitHub sendiri);
- cermin: link Drive lama, dicoba **otomatis** kalau sumber utama gagal.

Terukur di cermin baru:

| Permintaan | Balasan |
|---|---|
| `Range: bytes=0-0` | `206`, `content-range: bytes 0-0/712800749` |
| `Range: bytes=712800748-` | `206` |
| `Range: bytes=712800749-` | `416`, **`content-range: bytes */712800749`** (Drive: HTML tanpa header) |
| kecepatan | ±19 MB/detik |

## 2. Bukti isi paketnya benar (bukan klaim)

Kode ekstraksi yang **sama dengan yang ada di APK** (JNI `sampunrar_jni.cpp` +
unrar 7.11, dijalankan sebagai alat uji host) pada arsip asli:

```
pass 1  -> GTA SAN ANDREAS FIXED.rar        612.599.927 B
pass 2  -> 412 file, 5.001.095.504 B  (15 detik)
           gta_sa.exe 14 MB · models 1,2 GB · audio 3,3 GB · data 21 MB · anim 270 MB
```

Jadi kalau unduhan selesai, datanya ada. Yang sering gagal justru *unduhannya*
(kuota Drive) atau *ruang penyimpanan* (butuh ±6,5 GB bebas).

## 3. Diagnostik: tidak boleh ikut mati

Diagnostik lama menjalankan semua pemeriksaan dalam satu try/catch besar:
satu panggilan jaringan yang melempar (Drive 403/416/timeout) menghapus seluruh
laporan dan menggantinya dengan `Diagnostik error: java.io.IOException…`.
Sekarang:

- tiap bagian punya try/catch sendiri — bagian yang gagal ditulis
  `(gagal: …)` dan sisanya tetap tampil;
- **LOG TERAKHIR ditaruh di awal**;
- ukuran folder dihitung maksimal 2 detik / 20.000 file (tidak menggantung
  menunggu 4,7 GB);
- ada baris `RUANG` vs `BUTUH` (paket sesuai tier) dan `SISA` (file sementara
  `.download`/`.rar` yang bikin resume kacau);
- ada tes per sumber paket: kode HTTP, tipe, ukuran total, plus arti 403/404;
- teks laporan tampil di kotak **monospace yang bisa digulir**, tombol
  **Salin** dan **Simpan** (`diagnostik.txt`).

## 4. Bug baru yang ketemu saat mengetes (dan sudah dibetulkan)

Unduhan ke GitHub mulanya gagal `HTTP 406` — bukan karena Range, tapi karena
`HttpURLConnection` mengirim header `Accept` bawaan ("text/html, image/gif, …").
Diukur: Accept bawaan → 406, `Accept: */*` → 200. Sekarang semua jalur unduhan
memakai `core/HttpTune` (UA + Accept + Accept-Encoding identity).

Selain itu 4xx saat *melanjutkan* unduhan (406/400/501) diperlakukan seperti
416: buang sisa, mulai dari nol — bukan menyerah.

## Uji

- `DownloadTest` 32 assertion + `CoreTest` — lulus.
- Uji ke cermin GitHub pakai kode APK:
  - file lokal 712.800.750 (kebesaran 1 byte) → 416 → ulang dari 0 → `Rar!`;
  - file lokal 712.800.749 (pas) → 416 → **selesai, 0 byte diunduh** (80 ms).
- Ekstraksi 412 file / 4,66 GB dari arsip asli — lulus (alat uji host).

## Belum

Menjalankan Wine di HP ARM64 — masih buntu yang sama (sandbox x86 tanpa Android).
