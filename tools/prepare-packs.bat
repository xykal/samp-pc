@echo off
REM ============================================================
REM  prepare-packs.bat — Windows
REM  Dari "TUTOR GTA SAMP PC.rar" (Drive) -> folder game siap upload
REM  + 3 ZIP paket (extreme / lite / full) untuk hosting.
REM
REM  Butuh: 7-Zip terpasang (https://7-zip.org) — cek path di bawah.
REM  Jalankan: klik dua kali, atau dari cmd di folder repo.
REM ============================================================
setlocal enabledelayedexpansion
chcp 65001 >nul

set SEVENZIP="C:\Program Files\7-Zip\7z.exe"
if not exist %SEVENZIP% set SEVENZIP="C:\Program Files (x86)\7-Zip\7z.exe"
if not exist %SEVENZIP% (
  echo [X] 7-Zip tidak ditemukan. Install dulu dari https://7-zip.org
  pause & exit /b 1
)

set SRC_RAR=%~1
if "%SRC_RAR%"=="" set SRC_RAR=TUTOR GTA SAMP PC.rar
if not exist "%SRC_RAR%" (
  echo [X] File tidak ditemukan: "%SRC_RAR%"
  echo     Taruh file rar dari Drive di folder ini, atau drag ke script.
  pause & exit /b 1
)

set WORK=%~dp0work
set GAME=%WORK%\game
set OUT=%~dp0packs

echo.
echo [1/4] Ekstrak RAR luar...
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%"
%SEVENZIP% x -y -o"%WORK%" "%SRC_RAR%" >nul || (
  echo [X] Gagal ekstrak RAR luar
  pause & exit /b 1
)

echo [2/4] Ekstrak RAR dalam (GTA SAN ANDREAS FIXED.rar)...
if exist "%WORK%\GTA SAN ANDREAS FIXED.rar" (
  mkdir "%WORK%\inner"
  %SEVENZIP% x -y -o"%WORK%\inner" "%WORK%\GTA SAN ANDREAS FIXED.rar" >nul
  if exist "%WORK%\inner\GTA SAN ANDREAS FIXED" (
    move "%WORK%\inner\GTA SAN ANDREAS FIXED" "%GAME%" >nul
  ) else (
    move "%WORK%\inner\*" "%GAME%" >nul 2>&1
  )
) else (
  echo     [!] RAR dalam tidak ditemukan, coba pakai isi folder langsung
  if exist "%WORK%\GTA SAN ANDREAS FIXED" move "%WORK%\GTA SAN ANDREAS FIXED" "%GAME%" >nul
)

if not exist "%GAME%\gta_sa.exe" (
  echo [X] gta_sa.exe tidak ada di %GAME%
  echo     Cek struktur hasil ekstrak.
  pause & exit /b 1
)
echo     OK: %GAME%

echo [3/4] Rapikan komponen SA-MP + mod loader ke folder samp-install...
mkdir "%GAME%\samp-install" 2>nul
if exist "%WORK%\TUTOR GTA SAMP PC" (
  for %%F in ("%WORK%\TUTOR GTA SAMP PC\sa-mp-0.3.DL-R1-install.exe"
              "%WORK%\TUTOR GTA SAMP PC\SAMP_Addon_2.6_Setup.exe"
              "%WORK%\TUTOR GTA SAMP PC\CLEO4_setup.exe"
              "%WORK%\TUTOR GTA SAMP PC\modloader.zip"
              "%WORK%\TUTOR GTA SAMP PC\modloader.asi") do (
    if exist %%F copy /y %%F "%GAME%\samp-install\" >nul
  )
)

echo [4/4] Bikin ZIP per tier (extreme / lite / full)...
if not exist "%OUT%" mkdir "%OUT%"
for %%T in (extreme lite full) do (
  echo     - pack-%%T.zip ...
  pushd "%GAME%"
  %SEVENZIP% a -tzip -mx1 -bso0 -bsp0 "%OUT%\pack-%%T.zip" @packs\%%T.txt
  popd
)

echo.
echo ============================================================
echo  SELESAI
echo    Folder game : %GAME%
echo    ZIP paket   : %OUT%
echo.
echo  Langkah berikut:
echo    1. Upload isi %GAME% ke hosting (S3/VPS/Cloudflare R2) pada path
echo       yang sama dengan PACK_BASE_URL, MISAAL strukturnya:
echo         https://cdn.domainkamu.com/samp/gta_sa.exe
echo         https://cdn.domainkamu.com/samp/models/gta3.img
echo       (manifest_default.tsv sudah berisi 417 path + ukuran asli)
echo.
echo    2. ATAU upload pack-extreme.zip lalu set PACK_ARCHIVE_URL di
echo       gradle.properties.
echo ============================================================
pause
