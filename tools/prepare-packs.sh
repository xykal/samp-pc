#!/usr/bin/env bash
# prepare-packs.sh — Linux / macOS
# Dari "TUTOR GTA SAMP PC.rar" -> folder game siap upload + ZIP per tier.
#
# Butuh: p7zip (7z) atau unrar + zip.
#   Debian/Ubuntu : sudo apt install p7zip-full
#   macOS         : brew install p7zip
set -euo pipefail

SRC="${1:-TUTOR GTA SAMP PC.rar}"
ZIPPER="${ZIPPER:-7z}"
WORK="$(pwd)/work"
GAME="$WORK/game"
OUT="$(pwd)/packs"

[[ -f "$SRC" ]] || { echo "[X] tidak ada: $SRC"; exit 1; }
command -v "$ZIPPER" >/dev/null || { echo "[X] $ZIPPER tidak ada (install p7zip)"; exit 1; }

echo "[1/4] ekstrak rar luar…"
rm -rf "$WORK"; mkdir -p "$WORK"
"$ZIPPER" x -y -o"$WORK" "$SRC" >/dev/null

echo "[2/4] ekstrak rar dalam…"
if [[ -f "$WORK/GTA SAN ANDREAS FIXED.rar" ]]; then
  mkdir -p "$WORK/inner"
  "$ZIPPER" x -y -o"$WORK/inner" "$WORK/GTA SAN ANDREAS FIXED.rar" >/dev/null
  mv "$WORK/inner/GTA SAN ANDREAS FIXED" "$GAME" 2>/dev/null || mv "$WORK/inner"/* "$GAME"
else
  mv "$WORK/GTA SAN ANDREAS FIXED" "$GAME"
fi

[[ -f "$GAME/gta_sa.exe" ]] || { echo "[X] gta_sa.exe tidak ada"; exit 1; }
echo "    OK: $GAME ($(du -sh "$GAME" | cut -f1))"

echo "[3/4] rapikan komponen SA-MP…"
mkdir -p "$GAME/samp-install"
for f in sa-mp-0.3.DL-R1-install.exe SAMP_Addon_2.6_Setup.exe CLEO4_setup.exe modloader.zip modloader.asi; do
  [[ -f "$WORK/TUTOR GTA SAMP PC/$f" ]] && cp "$WORK/TUTOR GTA SAMP PC/$f" "$GAME/samp-install/"
done

echo "[4/4] bikin ZIP per tier…"
mkdir -p "$OUT"
for t in extreme lite full; do
  echo "   - pack-$t.zip"
  ( cd "$GAME" && "$ZIPPER" a -tzip -mx1 -bso0 -bsp0 "$OUT/pack-$t.zip" @"$(pwd)/../../packs/$t.txt" 2>/dev/null \
    || "$ZIPPER" a -tzip -mx1 -bso0 -bsp0 "$OUT/pack-$t.zip" "-i@$(pwd)/../../packs/$t.txt" )
done

cat <<EOF

============================================================
SELESAI
  folder game : $GAME
  ZIP paket   : $OUT
  ukuran      : $(du -sh "$OUT"/*.zip 2>/dev/null | awk '{print $1, $2}' | tr '\n' ' ')

Upload isi $GAME ke hosting pada path yang sama dengan PACK_BASE_URL,
atau upload salah satu ZIP dan set PACK_ARCHIVE_URL.
============================================================
EOF
