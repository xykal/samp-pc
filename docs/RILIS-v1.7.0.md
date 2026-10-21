# XversSamp v1.7.0 — progres kelihatan, ekstraksi terlacak

**Download (501 MB, arm64):**
https://github.com/xykal/samp-pc-download/releases/download/v1.7.0/xverssamp-v1.7.0-merged.apk

sha256: `d578bc14c0929ab7264072d76338d917f009d31311e1715194b34c4fdf1ddc6c`

---

## Kabar bagus dari Diagnostik-mu

Unduhannya **berhasil**: `712.800.749 byte` dalam 72 detik, HTTP 200,
tipe `application/octet-stream`, ukuran cocok. Jadi server Drive, link, dan
jaringan aman. Yang rusak itu **tampilan progress + tahap sesudahnya**.

## Kenapa progress-nya nggak ada (bug dari v1.4.0)

Fungsi progress punya tanda `done` (selesai). Masalahnya tanda itu gua pakai
juga untuk tahap **antara**: "Mengunduh paket…", "Membuka RAR dalam…",
"Memasang SA-MP…". Akibatnya:

1. begitu unduhan mulai, app menerima sinyal **"selesai"** → menampilkan
   "Paket siap", menghapus status progress, dan **berhenti memantau**;
2. sejak detik itu **tidak ada progress lagi** di layar;
3. "terpasang 0 B" karena pengecekan folder dilakukan saat itu juga
   (ekstraksi belum jalan);
4. setelah unduhan benar-benar selesai, layar tidak memberi kabar apa pun —
   persis seperti yang kamu alami.

**Sekarang:** tanda "selesai" hanya dipakai di akhir sekali. Tahap antara pakai
status "sedang jalan" dengan bar bergerak.

## Progress sekarang berbentuk kartu di layar utama

Bukan dialog lagi (dialog bisa ketutup, kelewat, atau hilang). Kartu
**"SEDANG DIKERJAKAN"** selalu muncul di atas:

```
SEDANG DIKERJAKAN
Mengunduh paket 42%  (285/679 MB)
[██████████░░░░░░░░░░░░░]
42%
Boleh ditinggal — unduhan jalan di latar belakang.
```

Tahap yang muncul berurutan:
`Menghubungi server…` → `Mengunduh paket xx%` → `Membuka RAR dalam…` →
`Mengekstrak 417 file… / Mengekstrak… 1200 MB` → `Mengunduh client SA-MP…` →
`Memasang SA-MP…` → **Paket siap**.

## Log sekarang mencatat tahap ekstraksi

Ini bagian yang belum pernah kelihatan sebelumnya. Di `install.log` akan ada:

```
RarNative tersedia=true
pass 1: cari *.rar
pass 1 hasil=1 file, 683000000 B
rar dalam: GTA SAN ANDREAS FIXED.rar (652 MB)
pass 2: 417 file -> /storage/.../GTA-SA
ekstrak 200 MB / 652 MB   (data/models/...)
ekstrak 400 MB / 652 MB
pass 2 hasil=417 file, 632000000 B
unzip client -> /storage/.../GTA-SA
selesai, ukuran folder=1680000000 B
isi folder: samp.exe, gta_sa.exe, models/, audio/, data/, ...
```

Kalau `RarNative tersedia=false` → artinya lib unrar tidak bisa dimuat di
perangkat itu (masalah ABI/emulator), dan itu akan langsung ditulis.

## Pagar: nggak ada lagi "selesai" palsu

Kalau setelah semua tahap `samp.exe` tetap tidak ada, app sekarang bilang
**gagal** sambil menyebut isi folder — bukan "selesai" lalu kamu bingung
kenapa "samp.exe belum ada".

Tombol **Jalankan** juga sudah diperbaiki: kalau data belum terpasang, muncul
dialog jelas + tombol **Unduh sekarang**.

## Diagnostik nambah 3 baris

```
RAR     : siap / TIDAK BISA DIMUAT
samp.exe: ada / belum ada
ISI     : samp.exe, models/, audio/, …
```

## Soal MEmu (jujur)

Diagnostik-mu menunjukkan `ABI: x86_64, arm64-v8a, x86, armeabi-v7a, armeabi`
dan `HP: Nubia NX809J` — itu profil khas **MEmu**. Artinya emulator x86 dengan
ARM translation.

- **Unduh + ekstrak** bisa jalan (itu Java + lib unrar arm64 lewat translation).
- **Engine-nya** (Wine + Box64) tetap butuh ARM64 asli. Di x86 dengan translation
  dobel, GTA kemungkinan besar tidak akan jalan normal.

Sekarang app menampilkan banner peringatan itu di layar utama, jadi jelas mana
yang bisa dites dan mana yang tidak.

## Cara tes

1. Install v1.7.0 di atas versi lama.
2. Buka app → **Pilih & unduh paket** → pilih tier (**LITE** paling cepat).
3. Kartu progress harus langsung muncul dan angkanya bergerak.
4. Selesai → dialog **"Paket siap"** + tombol **Jalankan**.
5. Kalau gagal → **Diagnostik** → **Salin** → kirim ke gua (sekarang log-nya
   memuat tahap ekstraksi yang detail).
