# XversSamp v1.8.0 — Drive 403, impor RAR, dan hasil tes mandiri

**Download (501 MB, arm64):**
https://github.com/xykal/samp-pc-download/releases/download/v1.8.0/xverssamp-v1.8.0-merged.apk

sha256: `6222961776d0c26e3f857a28357dd5e54477e1a26c1e4a16f19d60c5fad6a911`

---

## 1. Gua tes sendiri dulu — ini hasilnya

Kamu minta gua tes sendiri sebelum ngasih. Udah gua lakukan, pakai **kode
`DriveDownloader` yang sama** yang dipakai APK, dan **48 file source unrar yang
sama** yang dipakai JNI-nya:

| Yang dites | Hasil |
|---|---|
| Unduh penuh dari Drive | HTTP 200, `application/octet-stream`, **712.800.749 B dalam 6 detik** ✅ |
| Resume (mulai byte 64 MB) | **HTTP 206**, `Content-Range: bytes 67108864-712800748/712800749` ✅ |
| Pass 1 — buka RAR luar | dapat `GTA SAN ANDREAS FIXED.rar` (612.599.927 B) ✅ |
| Isi RAR dalam | 448 entri, root `GTA SAN ANDREAS FIXED/`, ada `gta_sa.exe`, **tidak ada** `samp.exe` (normal) |
| Manifest vs arsip | **412/412** path yang diminta ada di arsip ✅ |
| Pass 2 — ekstrak tier LITE | **2.4 GB** ✅ |
| Tempel `samp-client.zip` | **`samp.exe` + `gta_sa.exe` ADA**, `models/gta3.img`, `audio/streams/AA` ada ✅ |

**Artinya:** pipeline datanya benar — unduh → RAR dalam RAR → ekstrak per tier →
tempel client → `samp.exe` ada. Yang bikin kamu gagal **hanya Drive-nya nolak
(403)**, bukan kode ekstraksinya.

## 2. Kenapa Drive 403 dan apa yang gua ubah

HTTP **403** dari Google Drive untuk file besar hampir selalu berarti **kuota
file habis** — file itu sudah terlalu sering diunduh publik dalam waktu singkat
(dan kita berdua sudah beberapa kali menarik 712 MB dari file yang sama).
Google mengunci sementara, biasanya pulih beberapa jam.

Yang gua perbaiki:

1. **CookieManager aktif.** Alur halaman konfirmasi Google butuh cookie; tanpa
   cookie, permintaan kedua bisa ditolak 403. Ini kemungkinan besar penyebab
   versi sebelumnya.
2. **3 endpoint dicoba berurutan:** `drive.usercontent.google.com` →
   `drive.google.com/uc` → `docs.google.com/uc`. Kalau satu ditolak, lanjut.
3. **Pesan error yang jujur dan jelas** (bukan cuma "gagal"):
   - 403 → *"kuota file Drive habis. Tunggu beberapa jam, ganti link paket, atau
     impor file RAR yang kamu unduh lewat browser."*
   - 404 → link berubah / file dihapus
   - 429 → terlalu banyak permintaan
   Semua percobaan endpoint dicatat di `install.log`.
4. **Arsip nggak dihapus kalau belum sukses.** Dulu kalau gagal di tengah,
   harus mulai dari nol — dan kalau kuota habis, kamu mentok. Sekarang unduhan
   **lanjut dari byte terakhir** (`Range`).

## 3. Jalan pintas kalau Drive tetap blokir: Impor file

Tombol **"Impor dari file (ZIP / RAR)"** sekarang menerima **RAR** juga (dulu
cuma ZIP). Jadi kalau Drive nolak di app:

1. Buka link Drive di **Chrome** (browser biasanya dapat jatah kuota sendiri),
2. unduh `TUTOR GTA SAMP PC.rar` (712 MB),
3. buka app → **Impor dari file (ZIP / RAR)** → pilih file itu.

Prosesnya lewat service yang sama: progress bar jalan, `samp.exe` diverifikasi,
log tercatat. Persis seperti unduhan biasa, cuma sumbernya file lokal.

Dialog gagal sekarang punya tombol: **Diagnostik / Impor file / Coba lagi**.

## 4. Soal "bikin Wine sendiri? Box sendiri?"

Jawaban jujur: **bisa, tapi bukan itu masalahnya dan hasilnya bakal lebih buruk.**

- **Wine dan Box64 itu open source.** Winlator pada dasarnya = Wine + Box64 +
  rootfs + driver GL/Vulkan. Yang "dibikin" di sana memang sudah Wine & Box,
  cuma dirakit jadi satu APK.
- Bikin ulang dari nol artinya: cross-compile Wine untuk Android ARM64, bikin
  X server sendiri, bikin driver GL/Vulkan sendiri, lalu mensupport Box64
  (penerjemah x86 → ARM64). Itu kerjaan berbulan-bulan, dan hasilnya paling
  banter setara dengan yang sekarang.
- **Yang lebih penting untuk kasusmu:** di MEmu (x86_64) masalahnya bukan Wine
  atau Box, tapi **arsitektur**. x86 itu nggak butuh Box64 sama sekali — x86
  butuh Wine x86 native. Itu jalur build yang beda total (Wine 32/64-bit untuk
  Android x86 + driver). Jadi bikin "Wine sendiri" nggak akan bikin MEmu jalan.

Kesimpulan praktis: **engine yang ada sudah jalur tercepat**. Yang perlu
dibereskan urutannya: (a) Drive bisa diunduh, (b) data masuk ke folder yang
benar, (c) engine jalan di HP ARM64. Untuk (c), MEmu bukan alat uji yang sahih —
pakai HP ARM64.

## Cara tes v1.8.0

1. Install di atas versi lama.
2. Buka app → **Pilih & unduh paket** → **LITE**.
3. Kalau muncul "Gagal / 403":
   - tekan **Coba lagi** (kalau ada sisa file, lanjut dari byte terakhir),
   - atau unduh RAR-nya di Chrome lalu **Impor dari file (ZIP / RAR)**.
4. Setelah selesai → dialog **"Paket siap"** → **Jalankan**.

Kalau masih gagal, buka **Diagnostik → Salin** dan kirim. Sekarang isinya
menyebut endpoint mana yang ditolak dan HTTP code-nya.
