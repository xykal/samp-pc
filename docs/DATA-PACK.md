# Paket data — rincian asli

Total: **4.71 GB** dalam **417 file**.

| Tag | File | Ukuran | Isi |
|---|---|---|---|
| `speech` | 5 | 1.81 GB | suara ped/speech bank (SPC_*) — penghemat terbesar |
| `core` | 386 | 1.17 GB | models, data game, animasi, executable — wajib |
| `radio` | 13 | 0.89 GB | 13 file stasiun radio |
| `cutscene` | 2 | 0.38 GB | video cutscene (cuts.img) + audio cutscene |
| `sfx` | 4 | 0.33 GB | efek suara umum (mesin, tembakan) |
| `ads` | 2 | 0.09 GB | iklan radio & ambience |

## Tier yang dipakai app

| Tier | Ukuran | Isi |
|---|---|---|
| EXTREME (core+sfx) | 1.55 GB | core, sfx, optional |
| LITE (core+sfx+radio) | 2.44 GB | core, sfx, radio, optional |
| FULL (semua) | 4.71 GB | core, sfx, speech, radio, cutscene, ads, optional |

## Catatan

- `speech` (5 file SPC_*, 1,81 GB) adalah penghemat terbesar. Menghapusnya membuat dialog NPC senyap; gameplay & SA-MP tetap jalan, tapi **uji dulu** kalau gamemode kamu memutar suara dialog.
- `cutscene` hanya dipakai mode single-player/misi; SA-MP praktis tidak perlu.
- `radio` aman dibuang untuk menghemat 0,89 GB, atau diganti stream Icecast.
