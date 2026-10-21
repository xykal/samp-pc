# XversSamp v1.6.0 — unduhan diperbaiki + diagnostik

**Download (501 MB, arm64):**
https://github.com/xykal/samp-pc-download/releases/download/v1.6.0/xverssamp-v1.6.0-merged.apk

sha256: `65945de3c27f62f17b2119f69369bacbd4f920ce5a709d68f64e93388a79eee2`

---

## 1. Kenapa "gagal mengunduh" dan progress bar nggak muncul

Gua nemu **bug nyata** yang gua bikin sendiri di v1.4.0 — bukan salah HP kamu:

> Waktu gua pindahin proses unduhan ke proses terpisah (`:sync`, biar app
> nggak force close), service itu **masih baca link Drive dari
> SharedPreferences**. Masalahnya, `SharedPreferences` **tidak sinkron
> antar-proses** di Android. Jadi service bisa dapat link **kosong** →
> langsung gagal → nggak pernah sempat ngirim progress.

**Perbaikannya:** link paket + mode penyimpanan sekarang dikirim langsung lewat
Intent ke service, bukan lewat prefs. Ini yang paling mungkin bikin kasusmu.

Yang juga gua perbaiki sekalian:

| Masalah | Perbaikan |
|---|---|
| Sekali timeout = gagal total | **6x percobaan otomatis + resume** (lanjut dari byte terakhir, bukan mulai dari nol) |
| Cek ruang pakai ukuran hasil ekstrak → "ruang tidak cukup" palsu | cek setelah server kasih ukuran file asli, pesan pakai angka nyata |
| Bar **kosong** saat total belum diketahui (`-1` = nggak digambar) | sekarang ada **segmen berjalan** (indeterminate), jelas app masih kerja |

## 2. Baru: tombol Diagnostik — biar nggak nebak lagi

Di kartu **PAKET DATA** ada tombol **"Diagnostik (kalau gagal)"**. Isinya:

- versi app, nama paket, ABI, HP, Android
- folder game, mode (internal/SD), ruang bebas, ukuran terpasang
- URL sumber yang dipakai
- **TES KE SERVER**: HTTP code, tipe file, ukuran, waktu respons
- `install.log` terakhir (semua langkah unduhan + error)
- crash terakhir (kalau ada)

Ada tombol **Salin** → tinggal paste ke chat. Itu cukup buat gua tahu persis
kenapa unduhan gagal di perangkatmu.

Dialog gagal sekarang punya: **Coba lagi / Diagnostik / Lihat log**.

## 3. Baru: "Ganti link paket"

Kalau file Drive bawaan kena **limit kuota Google** ("Too many users have
viewed or downloaded this file"), app nggak bisa apa-apa. Sekarang kamu bisa
tempel link Drive salinanmu sendiri di menu **"Ganti link paket (opsional)"**,
dan app akan pakai itu.

## 4. Nama "Winlator" dihapus

- Semua teks resource engine (`res/values/*.xml` + XML lain) → **XversSamp**
- String literal di kode engine juga diganti — **nama class & package tidak
  disentuh**, karena dipanggil JNI/refleksi (kalau diganti, engine crash)
- Label app, judul, dialog, notifikasi: **XversSamp**

Sisa kata "Winlator" di dalam APK cuma **nama resource internal**
(`BTChooseWinlatorPath` dll.) dan nama class — **tidak tampil di layar mana pun**.

## 5. Repo nggak private lagi ✅

`xykal/samp-pc` sudah gua ubah dari **private → public**:
https://github.com/xykal/samp-pc

Sebelum diubah, gua pindai dulu tree + **seluruh riwayat git** untuk pola
`ghp_`/`github_pat_`/`GITHUB_TOKEN=`/password — hasilnya bersih, nggak ada
token atau kredensial yang ikut kecatat. Repo download-nya (`samp-pc-download`)
juga tetap public.

## Cara tes

1. Install v1.6.0 (langsung di atas versi lama, nggak perlu uninstall).
2. Buka app → **Pilih & unduh paket** → pilih tier.
3. Progress bar harus langsung muncul (ada animasi jalan walaupun ukuran belum diketahui).
4. Kalau tetap gagal: buka **Diagnostik (kalau gagal)** → **Salin** → kirim ke gua.

Kalau kamu mau, screenshot layar Diagnostik-nya juga boleh — dari situ kelihatan
HTTP code dan isi `install.log` sekaligus.
