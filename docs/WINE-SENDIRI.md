# Engine sendiri — Wine 10.17 bionic ARM64 (SELESAI dibangun, BELUM diuji di HP)

Status per 2026-10-22: **layer Wine sendiri sudah jadi dan sudah masuk APK v1.9.0.**
Yang belum: menjalankannya di HP ARM64 (sandbox kerja saya x86 tanpa Android).

---

## 1. Hasil akhir

| Item | Nilai |
|---|---|
| Source | `mihsian77/wine-10.17-winlator` (Wine 10.17 + patch Winlator) |
| Runner | `ubuntu-24.04` (x86_64), NDK **r27c** |
| Target unix | `aarch64-linux-android28` (bionic) |
| PE 32-bit | `i686-w64-mingw32-gcc` (gcc-mingw, **bukan** llvm-mingw) |
| Grafis | `winex11.drv` (bukan `wineandroid.drv`) |
| Layer jadi | `wine-10.17-bionic-arm64-v2.tar.zst` — **32.794.360 B (31,3 MiB)** |
| sha256 | `2ab2c2d20357a9bbebe07a2ac6e2608db233f8f3982b0f70fbf94a43ac74ecb6` |
| Isi | `bin/` 15 file, `lib/wine/i386-windows` **595 DLL + 109 EXE**, `lib/wine/aarch64-unix` 28 .so, `share/wine` (nls, fonts, wine.inf) |
| Uncompressed | 164 MB |
| Layer bawaan sebagai pembanding | `proton-9.0-arm64ec.tar.zst` 73.570.637 B (70,2 MiB), 515 MB isi |
| Workflow | `.github/workflows/build-wine-android.yml` (ID 392212432) |
| Run sukses | 39701770654 (build v1) → 39709077273 (build v2, program inti lengkap) |
| APK | v1.9.1, 460.142.079 B, sha256 `a5966ba3…d5a4` |

Bukti tipe file (dari log CI):

```
bin/wine:                        ELF 64-bit LSB pie executable, ARM aarch64,
                                 interpreter /system/bin/linker64   ← bionic, bukan glibc
bin/wineserver:                  ELF 64-bit LSB pie executable, ARM aarch64, /system/bin/linker64
lib/wine/aarch64-unix/ntdll.so:  ELF 64-bit LSB shared object, ARM aarch64
lib/wine/i386-windows/ntdll.dll: PE32 executable (DLL) Intel 80386
```

`DT_NEEDED` seluruh `aarch64-unix/*.so` + `bin/*` hanya: `libc.so`, `libdl.so`,
`libm.so`, `libX11.so.6`, `libXext.so.6` — semuanya ada di `imagefs/usr/lib`
milik APK (bionic, ARM64).

---

## 2. Resep yang berhasil (jangan diubah tanpa alasan)

### Stage 0 — native tools (host)

```sh
mkdir tools-build && cd tools-build
with_x=no with_freetype=no with_fontconfig=no \
../wine-src/configure --prefix=$PWD/../tools-inst \
  --enable-archs=x86_64 --without-x --without-freetype --without-fontconfig \
  --disable-tests --disable-win16
make -j"$(nproc)" __tooldeps__
make -C nls -j"$(nproc)"
```

Wajib `gcc-multilib g++-multilib` (kalau tidak: “Cannot build a 32-bit program”).

### Dependensi host

```
build-essential gcc-multilib g++-multilib bison flex pkg-config gettext \
gcc-mingw-w64-i686 g++-mingw-w64-i686 binutils-mingw-w64-i686 \
xz-utils zstd curl unzip file \
libx11-dev libxext-dev libxrender-dev libxrandr-dev libxfixes-dev \
libxcursor-dev libxi-dev libxcomposite-dev libxinerama-dev libxcb1-dev \
libxxf86vm-dev libxxf86dga-dev libxv-dev      ← WAJIB, lihat §3
```

### Header X11 — folder terpisah

```sh
mkdir -p /opt/xinc && cp -r /usr/include/X11 /opt/xinc/
[ -d /usr/include/xcb ] && cp -r /usr/include/xcb /opt/xinc/
```

**Jangan pernah** `--x-includes=/usr/include`: `pthread.h` glibc host akan menimpa
header bionic NDK → `'regparm' is not valid on this platform`.

### Lib bionic untuk link (dari imagefs APK)

Ekstrak `assets/imagefs.tar.zst` → `/opt/imagefs` **termasuk symlink**
(`libX11.so -> libX11.so.6.4.0`). Kalau symlink dilewat, `ld.lld` gagal `-lX11`.

### Configure cross

```sh
CC=aarch64-linux-android28-clang
export ac_cv_func_getpwent=no ac_cv_func_getpwnam=no ac_cv_func_getpwuid=no \
       ac_cv_func_sigsetmask=no ac_cv_func_fallocate=no ac_cv_func_strverscmp=no \
       ac_cv_func_getusershell=no
export ac_cv_lib_soname_X11=libX11.so.6 ac_cv_lib_soname_XEXT=libXext.so.6 \
       ac_cv_lib_soname_XRENDER=libXrender.so.1 ac_cv_lib_soname_XRANDR=libXrandr.so.2 \
       ac_cv_lib_soname_XFIXES=libXfixes.so.3 ac_cv_lib_soname_XCURSOR=libXcursor.so.1 \
       ac_cv_lib_soname_XCOMPOSITE=libXcomposite.so.1 ac_cv_lib_soname_XI=libXi.so.6 \
       ac_cv_lib_soname_XINERAMA=libXinerama.so.1 ac_cv_lib_soname_X11_XCB=libX11-xcb.so.1 \
       ac_cv_lib_soname_Xxf86vm=libXxf86vm.so.1

with_x=yes enable_wineandroid_drv=no with_freetype=no with_fontconfig=no with_vulkan=no \
../wine-src/configure \
  --host=aarch64-linux-android --prefix=/opt/wine-ours \
  --with-wine-tools=../tools-build \
  --enable-archs=i386 --with-mingw=i686-w64-mingw32 \
  --with-x --x-includes=/opt/xinc --x-libraries=/opt/imagefs/usr/lib \
  --enable-conhost=i386 --enable-plugplay=i386 --enable-rpcss=i386 \
  --enable-services=i386 --enable-spoolsv=i386 --enable-termsv=i386 \
  --enable-wineboot=i386 --enable-winemenubuilder=i386 --enable-wuauserv=i386 \
  --disable-tests --disable-win16
```

Link:
```sh
export LIBRARY_PATH=/opt/imagefs/usr/lib
export LDFLAGS="-L/opt/imagefs/usr/lib -Wl,-rpath-link,/opt/imagefs/usr/lib -fuse-ld=lld"
make -j"$(nproc)"
make install STRIP=true
```

### Tata letak hasil install (khas Winlator, host aarch64)

`configure.ac` baris 1012: `aarch64) exec_prefix='${prefix}/arm64-v8a'`.
Jadi:

- `bin/`, `lib/` → `<prefix>/arm64-v8a/…`
- `share/` → `<prefix>/share/…`
- loader `wine` + `wine-preloader` ada di `lib/wine/aarch64-unix/`, dan `bin/wine`
  **tidak** dibuat otomatis → di workflow disalin jadi file nyata
  (ekstraktor tar di Android tidak selalu bikin symlink).

### Paket akhir

```
bin/                    (wine, wineserver, wine-preloader, tools; symlink → file nyata)
lib/wine/i386-windows/  (595 PE i386, sudah di-strip i686-w64-mingw32-strip --strip-all)
lib/wine/aarch64-unix/  (28 .so ARM64 bionic)
share/wine/             (nls, fonts, wine.inf, winmd)
```
Buang `*.a`, `*.def`, `include/`, `share/man`, `share/doc`.
Tar + `zstd -19` → **31,0 MiB**.

---

## 3. Jebakan yang sudah kena (jangan diulang)

| Gejala | Sebab | Perbaikan |
|---|---|---|
| `Cannot build a 32-bit program` | host tanpa multilib | `gcc-multilib g++-multilib` + `--enable-archs=x86_64` di stage 0 |
| `FreeType 64-bit development files not found` | error datang dari **stage 0**, bukan configure cross | `--without-freetype --without-fontconfig` di stage 0 |
| `set_window_opengl_drawable undeclared` + target `wine-debug.apk` | `wineandroid.drv` butuh gradle/header Android | `enable_wineandroid_drv=no` + `--with-x` |
| `pthread.h: 'regparm' is not valid` | `-I/usr/include` glibc host | header X11 di `/opt/xinc`, jangan pernah `/usr/include` |
| `SONAME_LIBX11 undeclared` | cross configure tidak bisa tes `-lX11` | isi `ac_cv_lib_soname_*` manual |
| `ld.lld: unable to find -lX11` | (a) `-L` hilang karena tiap step shell baru, (b) symlink imagefs tidak diekstrak | `-L` + `LIBRARY_PATH` di step compile **dan** ekstrak symlink |
| `XF86VidModeSetGamma undeclared` | xorgproto hanya punya `xXF86VidMode*` | `libxxf86vm-dev libxxf86dga-dev libxv-dev` |
| Build sukses tapi `ls bin` gagal | install ke `<prefix>/arm64-v8a/bin` | path ABI diperhitungkan di step paket |
| `wineboot.exe`, `services.exe`, `rpcss.exe`, `conhost.exe`, dst tidak dibangun | `configure.ac`: `enable_<program>` default `$HOST_ARCH` (aarch64); PE arch kita cuma i386 → **tidak ada rule** | `--enable-<program>=i386` untuk kesembilannya |
| Layer kita ditimpa stock di APK | `app/build.gradle` fork punya task `downloadProton` yang validasi size+sha stock | timpa `protonURL/protonSHA256/protonSize` ke milik kita di build workflow |

---

## 4. Yang belum / risiko

1. **Belum dijalankan di HP ARM64.** Tidak ada perangkat di sandbox saya.
   Validasi runtime (wineboot, prefix, box64/FEX, driver GPU) harus dari HP kamu.
2. Layer ini **i386 + aarch64-unix saja** (wow64 murni) — tanpa `aarch64-windows`.
   Cukup untuk SA-MP 32-bit; app ARM64EC native tidak akan jalan.
   Container pattern bawaan (`proton-9.0-arm64ec_container_pattern.tzst`) masih
   dipakai untuk isi prefix; DLL bawaan di prefix berasal dari wine 9.0, sedangkan
   builtin yang benar-benar di-load wine tetap dari layer kita.
3. Kalau wine 10.17 menolak prefix wine 9.0, solusinya bikin ulang prefix
   (`wineboot -u`) atau ganti container pattern. Ini belum bisa saya uji.
4. Fork 4.1 sekarang **mengunduh** `imagefs.tar.zst` + `proton-9.0-arm64ec.tar.zst`
   dari internet saat build. Build kita menanam keduanya sebagai aset APK
   (zero setup) dan mem-patch ukuran/sha di `app/build.gradle` supaya file kita
   tidak dihapus task `downloadProton`.
