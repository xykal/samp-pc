#!/usr/bin/env python3
"""
make_pack_zips.py — dari manifest.tsv + folder data, bikin ZIP per tier.

Pakai ini kalau kamu mau hosting SATU arsip (mode PACK_ARCHIVE_URL di app),
bukan per-file.

    python3 make_pack_zips.py --dir "/var/tmp/pack/gta" --out /var/tmp/packs

Menghasilkan:
    pack-extreme.zip   core + sfx + optional
    pack-lite.zip      + radio
    pack-full.zip      + speech + cutscene + ads

Catatan: ZIP tidak dikompresi ulang besar-besaran (models/*.img sudah
terkompresi internal), jadi pakai ZIP_STORED untuk file > 50 MB supaya
prosesnya cepat dan ukuran tidak membengkak.
"""
import argparse
import pathlib
import sys
import zipfile

TIERS = {
    "extreme": {"core", "sfx", "optional"},
    "lite": {"core", "sfx", "radio", "optional"},
    "full": {"core", "sfx", "speech", "radio", "cutscene", "ads", "optional"},
}


def read_manifest(path: pathlib.Path):
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        p = line.split("\t")
        if len(p) < 3:
            continue
        rows.append((p[0], int(p[1]), p[2]))
    return rows


def build(rows, root: pathlib.Path, out: pathlib.Path, tier: str, tags: set):
    subset = [(p, s) for p, s, t in rows if t in tags]
    target = out / f"pack-{tier}.zip"
    total = sum(s for _p, s in subset)
    print(f"\n== {tier}: {len(subset)} file, {total/1073741824:.2f} GB -> {target}")
    done = 0
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for path, size in subset:
            src = root / path
            if not src.exists():
                print(f"   !! hilang: {path}")
                continue
            comp = zipfile.ZIP_STORED if size > 50 * 1024 * 1024 else zipfile.ZIP_DEFLATED
            z.write(src, arcname=path, compress_type=comp)
            done += size
            if done % (256 * 1024 * 1024) < size:
                print(f"   {done/1073741824:.2f} GB…")
    print(f"   selesai: {target} ({target.stat().st_size/1073741824:.2f} GB di disk)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="folder data game hasil ekstrak")
    ap.add_argument("--manifest", default="app/app/src/main/assets/manifest_default.tsv")
    ap.add_argument("--out", default="packs")
    ap.add_argument("--only", default="", help="extreme / lite / full (koma)")
    a = ap.parse_args()

    root = pathlib.Path(a.dir)
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = read_manifest(pathlib.Path(a.manifest))
    if not rows:
        sys.exit("manifest kosong / tidak ditemukan")

    wanted = [t.strip() for t in a.only.split(",") if t.strip()] or list(TIERS)
    for tier in wanted:
        build(rows, root, out, tier, TIERS[tier])


if __name__ == "__main__":
    main()
