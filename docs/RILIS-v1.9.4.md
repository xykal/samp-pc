# Rilis v1.9.4 — soal MEmu (jujur) + paket PC sekali klik

Tanggal: 2026-10-22

## Keluhan

> "gua pake memu njir harus bisa memu, harus work, harus gada error,
>  lu harus tes dulu menyeluruh tes apk nya langsung"

## Hasil pemeriksaan APK v1.9.3 (bukan asumsi)

```
$ unzip -l xverssamp-v1.9.3.apk | grep lib/
       25  lib/arm64-v8a/*
        0  lib/x86_64/*
        0  lib/x86/*
```

Semua native library hanya untuk **arm64-v8a**. Engine di dalamnya adalah
Wine + Box64 dari Winlator, yang menurut dokumentasi resminya butuh
**Android 9+ dan CPU ARM64**. MEmu/LDPlayer/BlueStacks berjalan di **x86_64**.

Kesimpulan teknis: di MEmu aplikasi bisa terbuka, data bisa diunduh, tetapi
proses Windows (samp.exe) tidak akan pernah bisa dieksekusi — Wine ARM64 tidak
bisa dijalankan di CPU x86. Bukan bug unduhan, bukan data hilang.

**Batasan pengujian di sisi kami:** sandbox tempat kerja ini x86_64 tanpa KVM
(`/dev/kvm` tidak ada, 1 GB RAM), jadi emulator Android tidak bisa dijalankan
di sini. Yang bisa dan sudah dilakukan: pemeriksaan isi APK, menjalankan kode
Java seluruh app dengan `javac` + framework Android, serta menguji kode
ekstraksi (C++ yang sama dengan APK) pada arsip asli.

## Yang berubah di v1.9.4

- Saat dibuka di perangkat non-ARM64, app **langsung memberi tahu** dan
  mengarahkan ke paket PC — bukan diam lalu game mati sendiri.

## Paket PC (Windows) — sekali klik

`XversSamp-PC.zip` (420 KB): `PASANG.bat` + `unrar.exe` (build sendiri dari
UnRAR 7.11) + `BACA-DULU.txt`.

Alur: unduh paket 712 MB → ekstrak lapisan RAR dalam → ekstrak 4,66 GB →
pasang client SA-MP → shortcut Desktop → main.

### Bukti uji (dijalankan)

- `unrar.exe` (PE32+ x86-64, statis) dijalankan di Wine:
  `unrar l TUTOR.rar` → `Details: RAR 5`, 52 entri, termasuk
  `GTA SAN ANDREAS FIXED.rar` 612.599.927 B.
- Perintah persis seperti di `PASANG.bat` (`x -y -o+ … "*.rar"`):
  arsip dalam ter-ekstrak 612.599.927 B, "All OK".
- Dari arsip dalam: `gta_sa.exe` 14.383.616 B ter-decode benar — metode
  RAR 7 yang tidak didukung 7-Zip 25.01 pun jalan (unrar 7.11).
- Ditemukan saat uji: arsip menyimpan satu folder induk
  (`GTA SAN ANDREAS FIXED/`), jadi `PASANG.bat` sekarang meratakan isinya
  (robocopy /MOVE) supaya `gta_sa.exe` ada di folder utama.

## Belum / tidak bisa

- Engine x86_64 (untuk MEmu) belum ada: itu porting Wine + imagefs ke
  x86_64 Android, bukan perbaikan bug.
