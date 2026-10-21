# "Engine sendiri" — apa artinya, bisa atau tidak

Dokumen ini jawaban langsung buat pertanyaan: *"maksudku engine sendiri"*.
Angka di bawah **hasil ukur APK v1.8.0** (bukan perkiraan).

---

## 1. Isi "engine" yang sekarang nempel di APK kita

| Komponen | Ukuran | Fungsinya | Bisa diganti? |
|---|---|---|---|
| `assets/imagefs.tar.zst` | **184,5 MB** | Linux rootfs: glibc, X server, PulseAudio, tools | bisa, tapi isinya ya sistem Linux |
| `assets/proton-9.0-arm64ec.tar.zst` | **70,2 MB** | **ini Wine-nya** (`bundled:proton-9.0-arm64ec`) | ya — harus build Wine sendiri |
| `assets/proton-9.0-*_container_pattern.tzst` | 18,1 MB | template container untuk proton | ikut hilang kalau Wine diganti |
| `assets/dxwrapper/*` (5 varian) | 31,3 MB | DXVK: Direct3D → Vulkan | ya (pilih 1) |
| `assets/graphics_driver/*` (5 varian) | 38,3 MB | driver GL/Vulkan (Adreno-nya, zink, freedreno) | ya (pilih sesuai GPU) |
| `assets/wincomponents/direct3d.tzst` | 29,8 MB | d3d native Windows | sebagian |
| `assets/container_pattern_common.tzst` | 38,6 MB | template container dasar | butuh |
| `assets/box64/box64-0.4.2.tzst` | 3,8 MB | penerjemah x86_64 → ARM64 | ya (bisa build sendiri) |
| `lib/arm64-v8a/*.so` | 10,9 MB | JNI kita + engine (X server, input, audio) | ya |

**Kesimpulan penting:** "engine" = **Wine + Box64 + rootfs Linux + driver grafis + lapisan
Android (X server, input, audio)**. Winlator bukan engine misterius — dia **rakitan** dari
komponen open-source itu. Jadi "bikin engine sendiri" = merakit ulang komponen yang sama,
atau mengganti salah satunya dengan buatan kita.

---

## 2. Tiga versi "engine sendiri"

### Opsi A — Engine ramping buatan kita (paling realistis) ⭐
Bukan Wine baru. Yang kita lakukan: **buang seluruh UI/app Winlator**, sisakan runtime
yang benar-benar dipakai SA-MP, dan pangkas aset ganda.

| Yang dipangkas | Hemat |
|---|---|
| `default_music.mp3` (lagu tema Winlator) | 7,6 MB |
| DXVK varian ganda → simpan 1 (1.10.3 async atau 2.3.1 gplasync) | ±14 MB |
| driver ganda → simpan Adreno + zink saja | ±15 MB |
| pattern proton x86_64 (tidak dipakai di ARM64) | 8,3 MB |
| inputcontrols/icons & profiles (UI Winlator) | 1,1 MB |
| activity/fragment/settings Winlator (dex) | ±2 MB |
| **Total** | **±48 MB** → APK ±452 MB |

Kalau mau lebih agresif lagi (butuh uji di HP): buang varian driver yang tidak cocok dengan
GPU user (zink/freedreno) → total ±75 MB → **APK ±425 MB**.

**Risiko:** sedang. Salah pangkas = container gagal dibuat. Karena itu dipangkas bertahap,
tiap tahap kamu tes 1 build. Waktu gua: 1–2 hari kerja + beberapa siklus build.

### Opsi B — Ganti Wine-nya dengan build sendiri
Ambil source Wine, build untuk Android ARM64 (`wine-9/10` + patch Android), ganti
`proton-9.0-arm64ec` dengan hasil build kita, hapus proton sama sekali.

- Effort: **minggu–bulan**. Butuh cross-compile toolchain, rootfs yang cocok, dan **perangkat
  ARM64 nyata** untuk tiap iterasi.
- Untung: tidak tergantung Proton, bisa lebih kecil (±70 MB bisa jadi 40 MB).
- Rugi: semua bug Wine jadi milik kita; game yang sekarang jalan bisa malah rusak.

### Opsi C — Dari nol (tulis Wine/loader sendiri)
Realistis? **Tidak.** Bukan karena tidak mungkin secara teori, tapi: Wine itu 30+ tahun kerja,
`user32`/`kernel32`/`d3d8`/`dinput` untuk GTA:SA butuh ribuan panggilan API. Dan untuk
**MEmu (x86)** malah salah arah — x86 tidak butuh penerjemah, dia butuh Wine x86 native.

---

## 3. Batasan yang harus kamu tahu (ini jujur, bukan alasan)

Gua **tidak punya HP ARM64 atau emulator ARM64** di sandbox. Yang bisa gua lakukan:
- bikin APK, audit isinya, jalankan logika non-Android (unduh, unrar, struktur file) → **sudah**;
- **tidak bisa** menjalankan Wine/Box64 untuk melihat game-nya hidup.

Artinya: untuk Opsi B/C, gua harus "ngoding buta" — dan itu hampir pasti gagal. Untuk
Opsi A, risikonya kecil karena cuma **membuang bagian yang tidak dipakai**, bukan menulis ulang.

---

## 4. Urutan yang gua sarankan

1. **Pastikan dulu data bisa masuk** (Drive 403 / impor RAR) — tanpa ini, engine apa pun tidak
   ada gunanya. → v1.8.x
2. **Tes jalan di HP ARM64** (bukan MEmu). Ini satu-satunya cara tahu engine-nya benar-benar
   hidup, dan jadi patokan sebelum ada yang dipangkas.
3. **Opsi A**: engine ramping, bertahap, tiap langkah dites.
4. **Opsi B** hanya kalau (3) sudah stabil dan kamu mau investasi waktu lebih.

---

## 5. Ringkas

- "Engine sendiri" **bisa**, dan versi yang masuk akal sekarang = **Opsi A** (rakitan kita
  sendiri, tanpa UI Winlator, ±450 MB, turun dari 501 MB).
- Menulis Wine baru = bulanan + tidak bisa gua uji tanpa perangkat ARM64, dan **tidak**
  menyelesaikan masalah MEmu (x86 butuh Wine x86, bukan penerjemah).
- Semua kerjaan tetap di repo public `xykal/samp-pc` + rilis di `xykal/samp-pc-download`.
