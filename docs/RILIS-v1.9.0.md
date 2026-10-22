# Rilis v1.9.0 — engine sendiri (Wine 10.17 bionic ARM64)

Tanggal: 2026-10-22

## Yang berubah

Layer Wine di dalam APK **bukan lagi** `proton-9.0-arm64ec` bawaan fork.
Sekarang layer itu hasil build sendiri di GitHub Actions:

| | v1.8.1 | v1.9.0 |
|---|---|---|
| Layer Wine | proton-9.0-arm64ec (stock) | **Wine 10.17 bionic ARM64** |
| Ukuran layer | 73.570.637 B | **32.514.687 B** |
| PE 32-bit | 786 DLL | 595 DLL (build kita) |
| Unix side | aarch64-unix wine 9.0 | aarch64-unix wine 10.17 |
| APK | 500.815.863 B | **459.859.571 B** |
| sha256 APK | `d483d512…ba18` | `5630bdb698e5428aa83d8e88a87e12aa01bd6249ee022a3951c38fcc30588b11` |

## Bukti verifikasi (CI + bedah APK)

- `bin/wine`, `bin/wineserver`: ELF ARM aarch64, interpreter `/system/bin/linker64`
- `lib/wine/aarch64-unix/ntdll.so`: ELF ARM aarch64, tanpa section debug
- `lib/wine/i386-windows/ntdll.dll`: PE32 Intel 80386
- `DT_NEEDED` eksternal: `libc.so`, `libdl.so`, `libm.so`, `libX11.so.6`,
  `libXext.so.6` → semua ada di `imagefs/usr/lib`
- Layer di `assets/proton-9.0-arm64ec.tar.zst` sha256 = `efd533ee…c1afe1`
- APK: `com.winlator.cmod`, app name **XversSamp**, minSdk 28, signed,
  `libsampunrar.so` ada, `imagefs.tar.zst` = 193.425.249 B

## Yang belum diuji

Menjalankan wine-nya di HP ARM64. Sandbox kerja saya x86 tanpa Android, jadi
validasi runtime (wineboot/prefix/box64-driver) harus dari HP kamu.
Kalau game tidak start, v1.8.1 masih bisa dipakai sebagai pembanding dan log
wine-nya bisa dikirim untuk iterasi berikutnya.

## Catatan teknis

Layer ini i386 + aarch64-unix (wow64 murni), tanpa `aarch64-windows` (arm64ec).
Cukup untuk SA-MP 32-bit; app ARM64EC native tidak jalan di layer ini.
