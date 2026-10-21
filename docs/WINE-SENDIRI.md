# Engine sendiri — status kerja (Wine kita sendiri)

Kamu pilih **Opsi B: Wine build sendiri**. Ini catatan kerjanya, apa yang sudah
jadi, dan apa yang masih tersisa. Semua angka di sini hasil ukur, bukan kira-kira.

---

## 1. Yang sudah jadi hari ini ✅

**Pabrik build-nya sudah hidup.** Workflow baru: `.github/workflows/build-wine.yml`
di repo `xykal/samp-pc`, jalan di runner **GitHub Actions ARM64 native**
(`ubuntu-24.04-arm`, gratis untuk repo publik).

Hasil percobaan kedua:

| Item | Hasil |
|---|---|
| Wine 9.0 dari source resmi (dl.winehq.org) | ✅ configure sukses |
| PE builtins `i386-windows` | ✅ 786 DLL (termasuk `ntdll.dll`, `wined3d.dll`, `mshtml.dll`) |
| Unix side `aarch64-unix` | ✅ `.so` ARM64 |
| Linker/compiler | ✅ gcc aarch64 native + `gcc-mingw-w64-i686` |
| Durasi build di CI | **±12 menit** |
| Artefak | `proton-ours.tar.zst` 132 MB (636 MB tidak terkompresi) |

Percobaan pertama gagal (llvm-mingw dipakai keliru → `libgcc.a not found`), percobaan
kedua dengan `--enable-archs=i386` **sukses**.

## 2. Temuan penting yang mengubah rencana 🔎

Waktu gua bedah `ntdll.so` dari layer Proton asli di APK:

```
ELF 64-bit LSB shared object, ARM aarch64, dynamically linked, **for Android**
```

Artinya Wine bawaan itu **di-compile pakai NDK (bionic / libc Android)**, bukan glibc.
Bedanya besar:

| | Build kita sekarang | Yang dibutuhkan engine |
|---|---|---|
| libc | glibc (Ubuntu) | **bionic (NDK)** |
| Target | `aarch64-linux-gnu` | `aarch64-linux-android28` |
| Bisa dipakai? | ❌ (butuh GLIBC_2.38, tidak ada di bionic) | ✅ |

Jadi artefak pertama itu **bahan uji pipeline**, belum bisa dipasang. Ini normal di
kerja engine: satu temuan mengubah target build.

## 3. Resep build yang benar (sudah ketemu)

Dari dokumentasi publik proyek builder Wine-Winlator + hasil bedah APK kita:

```
Tahap 0 : gcc host → native Wine tools (wrc, winebuild, widl) + file terjemahan (.mo)
Tahap 1 : NDK clang (aarch64-linux-android28)  → sisi Unix (.so, wineserver)
          llvm-mingw i686 (ucrt)               → PE i386-windows (.dll)
Paket   : tar + zstd → proton-9.0-arm64ec.tar.zst (struktur sama: bin/ lib/wine/ share/wine/)
```

Dua hal yang harus disiapkan di tahap berikutnya:
1. **NDK r27** (hanya tersedia untuk host x86_64 → build NDK harus di runner `ubuntu-24.04` biasa, bukan ARM).
2. **Patch Wine untuk bionic** — upstream Wine belum mendukung bionic; ini bagian
   yang paling banyak gagal dan paling lama (biasanya puluhan iterasi).

## 4. Perkiraan realistis

| Tahap | Status | Perkiraan |
|---|---|---|
| Pipeline build (CI) | ✅ selesai | — |
| Target bionic + patch | ⏳ berikutnya | beberapa hari iterasi build |
| Ganti layer di APK | ⏳ | 1 hari setelah build jalan |
| Uji jalan di HP | ⏳ | **butuh HP ARM64 dari kamu** (MEmu x86 tidak bisa menilai Wine) |
| Sama performanya dengan Proton sekarang | ⏳ | belum tentu; kita baru mulai |

Jujur: **belum akan lebih baik dari engine sekarang** dalam waktu dekat, dan
gua **tidak bisa menguji hasilnya** tanpa HP ARM64. Tapi jalurnya sudah konkret:
build kita sendiri, dari source, di CI kita sendiri.

## 5. Untungnya kalau nanti berhasil

- Tidak tergantung Proton pihak ketiga (bisa update Wine kapan saja).
- Bisa dipangkas: buang `mshtml`, `msxml3`, `wined3d` kalau tidak dipakai SA-MP
  (±45 MB), bahasa yang tidak perlu, debug symbols → layer bisa turun dari 70 MB ke ±35 MB.
- Bisa **dipatch khusus SA-MP** (mis. input, audio latency, kompatibilitas D3D8).

## 6. Yang tetap jalan sementara ini

Engine sekarang (Proton 9.0 arm64ec) **tidak gua sentuh** — supaya app tetap bisa
dipakai. Kerjaan Wine sendiri jalan di sampingnya, di workflow terpisah
(`build-wine.yml`), sampai hasilnya layak dipasang.

**Kebutuhan dari kamu (penting):** satu tes di **HP ARM64 asli** — bukan MEmu.
Tanpa itu, tidak ada cara tahu Wine kita hidup atau tidak.
