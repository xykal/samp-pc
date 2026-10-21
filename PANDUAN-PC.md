# Panduan PC — dari RAR Google Drive ke APK yang bisa main

Ditulis untuk yang **belum pernah pakai Android Studio / 7-Zip**. Semua langkah
dikerjakan di PC Windows, kecuali langkah 5 (di HP).

Total waktu: ±20 menit kerja + waktu upload.

---

## 0. Yang kamu punya sekarang

| Item | Status |
|---|---|
| APK gabungan (engine + frontend, 1 ikon) | ✅ sudah jadi — lihat link di `docs/STATUS.md` |
| Repo GitHub (build otomatis) | ✅ `github.com/xykal/samp-pc` |
| Paket data GTA:SA + SA-MP | ⏳ masih **RAR** di Google Drive → langkah 1–3 |
| Script auto (RAR → ZIP per tier) | ✅ `tools/prepare-packs.bat` |

**Kenapa RAR harus diubah?** Android tidak punya RAR bawaan dan app ini sengaja
tidak menambah library decompress 20 MB. ZIP bisa diekstrak native oleh Android,
jadi konversinya dilakukan sekali di PC.

---

## 1. Pasang 7-Zip (sekali saja)

1. Buka https://www.7-zip.org/download.html
2. Unduh **64-bit x64 .exe**, install dengan setting default.
3. Selesai. Script kita otomatis mencari `C:\Program Files\7-Zip\7z.exe`.

---

## 2. Siapkan folder kerja di PC

Buat satu folder, contoh `D:\SAMP-PC\`, isinya:

```
D:\SAMP-PC\
├─ rar\           <- taruh SEMUA part RAR di sini (part1, part2, ...)
├─ work\          <- hasil ekstrak (kosong dulu)
└─ tools\
   ├─ prepare-packs.bat
   ├─ make_manifest.py
   └─ packs\      <- daftar file per tier (extreme.txt, lite.txt, full.txt)
```

Ambil isi folder `tools/` dan `packs/` dari repo `xykal/samp-pc`
(tombol **Code → Download ZIP**, lalu ambil dua folder itu).

> Penting: **semua part RAR harus di folder yang sama.** Kalau kurang satu part,
> 7-Zip akan bilang "Cannot open file as archive".

---

## 3. Jalankan konversi (1 klik)

1. Klik dua kali `tools\prepare-packs.bat`.
2. Saat ditanya, tempel path folder RAR: `D:\SAMP-PC\rar`
3. Tempel path folder kerja: `D:\SAMP-PC\work`

Script akan:

1. mengekstrak RAR (semua part otomatis),
2. memindahkan `samp.exe`, `samp.dll`, folder `SAMP/` ke `work\samp-install\`,
3. membuat ZIP per tier dengan kompresi ringan (`-mx1`, cepat, ukuran wajar):

| File hasil | Isi | Ukuran |
|---|---|---|
| `work\pack-extreme.zip` | model/tekstur minimum + audio inti | ±1,55 GB |
| `work\pack-lite.zip` | + radio, cutscene, sebagian speech | ±2,44 GB |
| `work\pack-full.zip` | semua 417 file | ±4,71 GB |
| `work\samp-install.zip` | SA-MP 0.3.DL + Addon 2.6 + CLEO + modloader | ±58 MB |

**Saran buat HP kelas Helio G36: pakai `pack-extreme.zip`.** 1,55 GB,
sisanya bisa diunduh nanti dari dalam app kalau mau.

---

## 4. Taruh ZIP di Google Drive (atau hosting lain)

### Opsi A — Google Drive (paling gampang, sudah didukung app)

1. Upload `pack-extreme.zip` + `samp-install.zip` ke Drive.
2. Klik kanan file → **Share** → **Anyone with the link** → **Copy link**.
3. Link bentuknya:
   `https://drive.google.com/file/d/1AbCdEfGh.../view?usp=sharing`
4. **Tempel apa adanya** ke `pack.archiveUrl` (lihat langkah 5). App yang
   mengubahnya jadi unduhan langsung.

> Batas Drive: file >100 MB memang mendarat di halaman "scan virus", tapi app
> sudah menangani halaman itu (ambil `confirm` + `uuid` lalu unduh ulang).

### Opsi B — hosting/CDN sendiri

Serve ZIP-nya di URL apa pun yang bisa di-GET publik, misal
`https://cdn.domainkamu.com/samp/pack-extreme.zip`.

Kalau mau mode **per-file** (resume per file, hemat kuota kalau putus), serve
isinya apa adanya lalu buat manifest baru:

```bash
python tools/make_manifest.py --dir "D:\SAMP-PC\work\game" \
    --base-url "https://cdn.domainkamu.com/samp" --sha256 --write
```

---

## 5. Isi konfigurasi di APK

Ada dua cara; pilih yang nyaman.

### Cara cepat (tanpa build ulang)

Di HP, buka app → **Pengaturan** (ikon gerigi) → isi:

- **Server**: `ipserver-samp:7777`
- **Nickname**: nama karaktermu
- **Link paket**: tempel link Drive ZIP dari langkah 4

Nilai ini disimpan di app, jadi tidak perlu build ulang APK.

### Cara permanen (ikut di APK untuk semua orang)

Di repo, ubah `app/src/main/assets/app_config.properties`:

```properties
app.label=NamaKomunitas PC
server.default=ipserver-samp:7777
pack.baseUrl=
pack.archiveUrl=https://drive.google.com/file/d/1AbCdEfGh.../view?usp=sharing
```

Commit → workflow **Build Merged APK** jalan sendiri → APK baru keluar dengan
setting itu sebagai default.

---

## 6. Install APK di HP

1. Unduh `samp-pc-vX.Y.Z-merged-debug.apk` (±546 MB).
2. Izinkan **Install unknown apps** untuk browser/file manager.
3. Install. Ukuran terpasang ±1,3 GB (dex + imagefs + proton).
4. Buka app → pilih storage (**SD card** disarankan kalau internal tipis) →
   pilih tier → **Unduh paket** → tunggu (bisa ditinggal, ada notifikasi) →
   **Jalankan SA-MP**.

Saat pertama kali menekan **Jalankan SA-MP**, app menyiapkan container Wine
(sekali saja, ±1–2 menit). Kalau engine belum siap, app membuka layar setup
Winlator sekali; setelah itu tombol Play langsung masuk game, tanpa UI emulator.

---

## 7. Kalau gagal — cek ini dulu

| Gejala | Penyebab | Solusi |
|---|---|---|
| `Link Drive tidak dikenali` | link bukan link file | pakai Share → Copy link **file**, bukan folder |
| Drive menolak unduhan | belum "Anyone with the link" | ubah izin file |
| ZIP tidak ditemukan / 404 | file dipindah/dihapus | cek link masih hidup |
| `samp.exe belum ada` | paket belum diekstrak | ulangi Unduh paket |
| Layar hitam setelah Play | driver GPU belum cocok | app akan menurunkan tier otomatis; turunkan manual ke SAFETY di Pengaturan |
| Game jalan 8–12 fps | Helio G36 memang di bawah syarat GTA:SA PC | pakai `pack-extreme`, resolusi 640×360, tutup app lain |

---

## 8. Ringkasan perintah (buat yang mau manual)

```bat
:: ekstrak RAR
"C:\Program Files\7-Zip\7z.exe" x "D:\SAMP-PC\rar\*.rar" -o"D:\SAMP-PC\work\game"

:: ZIP sesuai daftar tier
"C:\Program Files\7-Zip\7z.exe" a -mx1 "D:\SAMP-PC\work\pack-extreme.zip" "@D:\SAMP-PC\tools\..\packs\extreme.txt"

:: manifest baru (kalau hosting sendiri)
python tools\make_manifest.py --dir "D:\SAMP-PC\work\game" --base-url "https://hosting-kamu/samp" --write
```
