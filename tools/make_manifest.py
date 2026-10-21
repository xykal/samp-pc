#!/usr/bin/env python3
"""
make_manifest.py — bikin manifest.tsv untuk app dari paket data yang sudah ada.

Dua mode:
  1) dari hasil listing RAR/ZIP (JSON):
       python3 make_manifest.py --from-json listing.json --base-url https://cdn.kamu.com/samp
  2) dari folder hasil ekstrak:
       python3 make_manifest.py --dir "/var/tmp/pack/gta" --base-url https://cdn.kamu.com/samp --sha256

Output:
  - app/app/src/main/assets/manifest_default.tsv
  - docs/DATA-PACK.md (rincian tier + ukuran)

Format manifest: path <TAB> size <TAB> tag <TAB> sha256 <TAB> url
Tag yang dipakai app: core, sfx, speech, radio, cutscene, ads, optional
"""
import argparse
import hashlib
import json
import os
import pathlib
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_TSV = ROOT / "app/app/src/main/assets/manifest_default.tsv"
OUT_DOC = ROOT / "docs/DATA-PACK.md"


def tag_for(path: str) -> str:
    n = path.lower().replace("\\", "/")
    base = n.split("/")[-1]

    if "/audio/sfx/" in n or n.startswith("audio/sfx/"):
        if base.startswith("spc_"):
            return "speech"
        return "sfx"
    if "/audio/streams/" in n or n.startswith("audio/streams/"):
        if base in ("cutscene",):
            return "cutscene"
        if base in ("adverts", "ambience"):
            return "ads"
        return "radio"
    if base == "cuts.img":
        return "cutscene"
    if n.startswith(("modloader/", "cleo/", "moonloader/", "mods/")):
        return "optional"
    return "core"


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n/1:.0f} {unit}" if unit == "B" else f"{n:.2f} {unit}"
        n /= 1024
    return str(n)


def rows_from_json(path: pathlib.Path):
    data = json.loads(path.read_text())
    out = []
    for item in data:
        name = item["n"]
        if item.get("d"):
            continue
        # buang prefix folder container ("GTA SAN ANDREAS FIXED/")
        parts = name.split("/")
        if len(parts) > 1 and parts[0].lower().startswith("gta san andreas"):
            name = "/".join(parts[1:])
        if not name or name.endswith("/"):
            continue
        out.append((name, int(item["s"])))
    return out


def rows_from_dir(root: pathlib.Path, do_sha: bool):
    out = []
    for dirpath, _dirs, files in os.walk(root):
        for f in files:
            full = pathlib.Path(dirpath) / f
            rel = str(full.relative_to(root)).replace("\\", "/")
            size = full.stat().st_size
            sha = "-"
            if do_sha:
                h = hashlib.sha256()
                with open(full, "rb") as fh:
                    for chunk in iter(lambda: fh.read(1 << 20), b""):
                        h.update(chunk)
                sha = h.hexdigest()
            out.append((rel, size, sha))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-json")
    ap.add_argument("--dir")
    ap.add_argument("--base-url", default="https://cdn.domainkamu.com/samp")
    ap.add_argument("--sha256", action="store_true")
    ap.add_argument("--write", action="store_true", help="tulis ke assets/manifest_default.tsv")
    args = ap.parse_args()

    if args.from_json:
        rows = [(p, s, "-") for p, s in rows_from_json(pathlib.Path(args.from_json))]
    elif args.dir:
        rows = rows_from_dir(pathlib.Path(args.dir), args.sha256)
    else:
        ap.error("pakai --from-json atau --dir")

    rows.sort()
    base = args.base_url.rstrip("/") + "/"
    lines = [
        "# Manifest paket data — dihasilkan make_manifest.py",
        "# path\tsize\ttag\tsha256\turl",
    ]
    per_tag = {}
    for path, size, sha in rows:
        tag = tag_for(path)
        per_tag.setdefault(tag, [0, 0])
        per_tag[tag][0] += 1
        per_tag[tag][1] += size
        url = base + urllib.parse.quote(path)
        lines.append(f"{path}\t{size}\t{tag}\t{sha}\t{url}")

    total = sum(v[1] for v in per_tag.values())
    tiers = {
        "EXTREME (core+sfx)": ["core", "sfx", "optional"],
        "LITE (core+sfx+radio)": ["core", "sfx", "radio", "optional"],
        "FULL (semua)": ["core", "sfx", "speech", "radio", "cutscene", "ads", "optional"],
    }

    print(f"file: {len(rows)} | total: {total/1073741824:.2f} GB")
    print(f"{'tag':<12}{'file':>7}{'ukuran':>14}")
    for tag, (n, sz) in sorted(per_tag.items(), key=lambda kv: -kv[1][1]):
        print(f"{tag:<12}{n:>7}{sz/1048576:>11.1f} MB")
    print()
    for name, tags in tiers.items():
        sz = sum(per_tag.get(t, [0, 0])[1] for t in tags)
        print(f"{name:<24}{sz/1073741824:>6.2f} GB")

    if args.write:
        OUT_TSV.parent.mkdir(parents=True, exist_ok=True)
        OUT_TSV.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\n> ditulis: {OUT_TSV}")

        OUT_DOC.parent.mkdir(parents=True, exist_ok=True)
        doc = ["# Paket data — rincian asli\n",
               f"Total: **{total/1073741824:.2f} GB** dalam **{len(rows)} file**.\n",
               "| Tag | File | Ukuran | Isi |", "|---|---|---|---|"]
        desc = {
            "core": "models, data game, animasi, executable — wajib",
            "sfx": "efek suara umum (mesin, tembakan)",
            "speech": "suara ped/speech bank (SPC_*) — penghemat terbesar",
            "radio": "13 file stasiun radio",
            "cutscene": "video cutscene (cuts.img) + audio cutscene",
            "ads": "iklan radio & ambience",
            "optional": "samp, addon, cleo, modloader",
        }
        for tag, (n, sz) in sorted(per_tag.items(), key=lambda kv: -kv[1][1]):
            row = "| `{}` | {} | {:.2f} GB | {} |".format(
                tag, n, sz / 1073741824, desc.get(tag, "-"))
            doc.append(row.replace(",", "") if False else row)
        doc.append("")
        doc.append("## Tier yang dipakai app\n")
        doc.append("| Tier | Ukuran | Isi |")
        doc.append("|---|---|---|")
        for name, tags in tiers.items():
            sz = sum(per_tag.get(t, [0, 0])[1] for t in tags)
            doc.append(f"| {name} | {sz/1073741824:.2f} GB | {', '.join(tags)} |")
        doc.append("")
        doc.append("## Catatan\n")
        doc.append("- `speech` (5 file SPC_*, 1,81 GB) adalah penghemat terbesar. Menghapusnya "
                   "membuat dialog NPC senyap; gameplay & SA-MP tetap jalan, tapi **uji dulu** "
                   "kalau gamemode kamu memutar suara dialog.")
        doc.append("- `cutscene` hanya dipakai mode single-player/misi; SA-MP praktis tidak perlu.")
        doc.append("- `radio` aman dibuang untuk menghemat 0,89 GB, atau diganti stream Icecast.")
        OUT_DOC.write_text("\n".join(doc) + "\n", encoding="utf-8")
        print(f"> ditulis: {OUT_DOC}")


if __name__ == "__main__":
    main()
