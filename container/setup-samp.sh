#!/usr/bin/env bash
# setup-samp.sh — dijalankan DI DALAM container (proot/Wine) atau lewat ADB.
# Menyiapkan GTA:SA + SA-MP tanpa user menyentuh layar desktop.
#
# Pemakaian:
#   ./setup-samp.sh --gta "/home/xuser/0/Games/GTA San Andreas" \
#                   --samp "/home/xuser/0/Downloads/sa-mp-0.3.7-R5-1-install.exe" \
#                   --nick "WargaBaru" --server "play.domainkamu.com:7777" \
#                   --fps 15 --driver virgl --res 640x360
#
# Argumen --fps/--driver/--res dihasilkan oleh ContainerPreset.toSetupArgs()
# di aplikasi Android, jadi nilainya sudah disesuaikan dengan HP user.
set -euo pipefail

GTA_DIR=""
SAMP_EXE=""
ADDON_EXE=""
CLEO_EXE=""
MODLOADER_DIR=""
NICK="Player"
SERVER=""
FPS="30"
DRIVER="turnip"
RES="1280x720"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gta)     GTA_DIR="$2"; shift 2 ;;
    --samp)    SAMP_EXE="$2"; shift 2 ;;
    --addon)   ADDON_EXE="$2"; shift 2 ;;
    --cleo)    CLEO_EXE="$2"; shift 2 ;;
    --modloader) MODLOADER_DIR="$2"; shift 2 ;;
    --nick)    NICK="$2"; shift 2 ;;
    --server)  SERVER="$2"; shift 2 ;;
    --fps)     FPS="$2"; shift 2 ;;
    --driver)  DRIVER="$2"; shift 2 ;;
    --res)     RES="$2"; shift 2 ;;
    *) echo "argumen tidak dikenal: $1"; exit 1 ;;
  esac
done

[[ -n "$GTA_DIR" ]] || { echo "ERROR: --gta wajib diisi"; exit 1; }
[[ -f "$GTA_DIR/gta_sa.exe" ]] || {
  echo "ERROR: gta_sa.exe tidak ditemukan di $GTA_DIR"
  echo "       GTA:SA harus versi 1.0 (bukan Steam 3.0)."
  exit 1
}

echo "==> GTA ditemukan: $GTA_DIR"
echo "==> preset: ${DRIVER} · ${RES} · ${FPS} fps"

if [[ -n "$SAMP_EXE" ]]; then
  echo "==> install SA-MP (senyap)..."
  # installer NSIS: /S = silent, /D = direktori tujuan (paling akhir, tanpa kutip)
  wine "$SAMP_EXE" /S /D="Z:${GTA_DIR}"
else
  echo "==> installer SA-MP dilewati (--samp kosong)"
fi

if [[ -n "$ADDON_EXE" ]]; then
  echo "==> install SAMP Addon (senyap)..."
  wine "$ADDON_EXE" /S /D="Z:${GTA_DIR}"
fi

if [[ -n "$CLEO_EXE" ]]; then
  echo "==> install CLEO 4 (senyap)..."
  wine "$CLEO_EXE" /S /D="Z:${GTA_DIR}"
fi

if [[ -n "$MODLOADER_DIR" && -d "$MODLOADER_DIR" ]]; then
  echo "==> copy modloader..."
  cp -r "$MODLOADER_DIR/." "$GTA_DIR/modloader/" 2>/dev/null || true
  [[ -f "$MODLOADER_DIR/../modloader.asi" ]] && cp "$MODLOADER_DIR/../modloader.asi" "$GTA_DIR/"
fi

echo "==> tulis samp.cfg"
HOST="${SERVER%%:*}"
PORT="${SERVER##*:}"
[[ -n "$HOST" ]] || HOST="127.0.0.1"
[[ -n "$PORT" && "$PORT" != "$HOST" ]] || PORT="7777"

cat > "$GTA_DIR/samp.cfg" <<EOF
# dibuat otomatis oleh setup-samp.sh
name=$NICK
host=$HOST
port=$PORT
page_size=10
font_face=Arial
font_size=8
EOF

echo "==> siapkan folder mod"
mkdir -p "$GTA_DIR/CLEO" "$GTA_DIR/modloader" "$GTA_DIR/moonloader" "$GTA_DIR/mods/_backup"

echo "==> batasi frame rate ke ${FPS} (physics GTA:SA dirancang 25-30 fps)"
[[ -f "$GTA_DIR/gta_sa.set" ]] && cp "$GTA_DIR/gta_sa.set" "$GTA_DIR/mods/_backup/gta_sa.set.bak"

echo "==> buat shortcut 'SA-MP PC'"
DESKTOP_DIR="${HOME:-/home/xuser}/Desktop"
mkdir -p "$DESKTOP_DIR"
cat > "$DESKTOP_DIR/samp-pc.desktop" <<EOF
[Desktop Entry]
Name=SA-MP PC
Exec=wine "$GTA_DIR/samp.exe"
Type=Application
Terminal=false
EOF

cat <<NOTES

==> selesai. Preset dipakai: ${DRIVER} / ${RES} / ${FPS} fps

Catatan penting:
  - Paket ini memakai SA-MP 0.3.DL-R1 (bukan 0.3.7). Server harus
    mengizinkan client 0.3.DL — cek rules "allowed_clients".
  - SAMP Addon mengubah renderer; kalau crash di GPU lemah (PowerVR/Mali),
    hapus folder "SAMP Addon" atau jalankan ulang tanpa --addon.
  - Suara tidak keluar? Ganti audio driver container ke Pulse.
  - Mouse/kamera liar? Aktifkan "Relative mouse" di input controls.
  - Crash saat masuk server? Matikan mod (CLEO/ASI) dulu — beberapa
    anti-cheat server menolak client yang dimodifikasi.
  - Helio G36 / PowerVR: kalau fps di bawah 10, jangan lanjut —
    pakai client native mobile atau streaming (lihat README).
NOTES
