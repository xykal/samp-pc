# Rilis v1.9.1 — engine sendiri, lengkap (Wine 10.17 bionic ARM64)

Tanggal: 2026-10-22 · APK 460.142.079 B
sha256 `a5966ba3f0133c9fdbc56e7133f2598315265ddcec25001bada64ed563cad5a4`

## Kenapa ada v1.9.1 (v1.9.0 keliru)

v1.9.0 sudah memakai layer Wine sendiri, tapi **14 program PE inti tidak ikut
dibangun**: `wineboot.exe`, `services.exe`, `rpcss.exe`, `plugplay.exe`,
`conhost.exe`, `spoolsv.exe`, `termsv.exe`, `winemenubuilder.exe`, `wuauserv.exe`,
`winevdm.exe`, `wow32.dll`, `w32skrnl.dll`, `belauncher.exe`, `dotnetfx35.exe`.

Sebabnya ada di `configure.ac`:
```sh
enable_services=${enable_services:-$HOST_ARCH}   # HOST_ARCH = aarch64
```
PE yang kita bangun cuma i386, jadi program-program itu tidak punya rule sama
sekali (`make: No rule to make target 'programs/services/i386-windows/services.exe'`).
Perbaikan: kesembilannya di-set eksplisit `=i386` saat configure.

## Bukti

| Item | v1.9.0 | v1.9.1 |
|---|---|---|
| PE i386 | 595 dll + 100 exe | 595 dll + **109 exe** |
| `wineboot.exe` / `services.exe` / `rpcss.exe` | ❌ | ✅ |
| Layer | 32.514.687 B | 32.794.360 B |
| sha256 layer | `efd533ee…c1afe1` | `2ab2c2d20357a9bbebe07a2ac6e2608db233f8f3982b0f70fbf94a43ac74ecb6` |
| APK | 459.859.571 B | 460.142.079 B |

Layer yang sama dipakai di APK (`assets/proton-9.0-arm64ec.tar.zst`, sha cocok),
`imagefs.tar.zst` 193.425.249 B, app name XversSamp, satu ikon LAUNCHER,
`libsampunrar.so` ada, signed.

## Yang belum

Uji jalan di HP ARM64. Semua verifikasi di atas statis (isi file, tipe ELF/PE,
sha256, isi APK). Belum ada sesi `wineboot` + GTA:SA di perangkat nyata.
