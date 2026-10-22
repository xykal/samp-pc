# Rilis v1.9.2 — download tahan putus (HTTP 416 dari Drive)

Tanggal: 2026-10-22 · APK 460.149.903 B
sha256 `107253be42191ffb1e0ea6636cb6409622d7d0ce5e965617cf7675a92e5b2fea`

## Masalah

User: **“gagal bre drive membalas HTTP 416”**.

File sementara `pack.download` lebih besar dari file asli di Drive (sisa sesi
yang pernah menulis halaman kuota/HTML, atau link berganti). Unduhan dilanjutkan
dengan `Range: bytes=<ukuran lokal>-` → Google membalas **416**.

Kode lama:
- `InstallService.downloadFile`: `if (code == 416) { log("sudah lengkap"); return; }`
  → dianggap sukses padahal isinya rusak;
- `DriveDownloader.get`: 416 dilempar sebagai error tanpa jalan keluar.

## Perilaku Google Drive yang diukur langsung (bukan asumsi)

| Permintaan | Balasan nyata |
|---|---|
| `Range: bytes=0-2` | `206`, `Content-Range: bytes 0-2/712800749` |
| `Range: bytes=712800748-` | `206`, `Content-Range: bytes 712800748-712800748/712800749` |
| `Range: bytes=712800749-` | **`416`**, `content-type: text/html`, **tanpa Content-Range** |
| `Range: bytes=999999999999-` | **`416`**, HTML `Error 416 (Bad Request)`, tanpa Content-Range |
| `Range: bytes=0-0` | `206`, `Content-Range: bytes 0-0/712800749` ← cara tahu total |

Karena 416 Drive **tidak** membawa `Content-Range`, "416 = sudah lengkap"
tidak bisa dipercaya; total harus ditanya lewat probe 1 byte.

Kasus kedua: OpenJDK melempar `IOException: Server returned HTTP response code: 416`
(alih-alih mengembalikan kode), dan pada alur redirect Drive kode itu baru muncul
sebagai *chained exception* saat body dibaca. Android mengembalikan kode normal —
dua-duanya harus tertangani.

## Perbaikan

1. `core/ResumePolicy` — aturan range murni:
   - `416` → `COMPLETE` hanya jika `Content-Range */total` == ukuran lokal;
     selain itu `RESTART`. Tanpa `Content-Range` → `RESTART` (atau probe dulu).
   - `206` → `APPEND` hanya jika awal range == ukuran lokal.
   - `200` → `RESTART` (server mengabaikan Range; kalau di-append jadi dobel).
   - `status(HttpURLConnection)` + `codeFromMessage()` → baca kode walau JVM melempar.
2. `core/ResumableDownload` — retry + resume + pemulihan 416, buang sisa HTML,
   probe total (`Source.knownTotal()`), validasi ukuran akhir.
3. `core/HttpStatusException` — kode status dibawa sebagai exception.
4. `core/DriveDownloader` — 416 diloloskan (tidak dianggap gagal akses),
   `probeTotal()` lewat `Range: bytes=0-0`, redirect/416 di OpenJDK ditangani.
5. `app/InstallService.downloadFile` — pakai ResumableDownload + cek magic
   `Rar!`/`PK` setelah unduh; kalau bukan arsip → buang & ulang sekali.
6. `core/PackDownloader` (mode per-file) — pulih dari 416, pakai ukuran manifest
   kalau server tidak memberi `Content-Range`.

## Uji (dijalankan, bukan diklaim)

`DownloadTest` — 32 assertion, server HTTP lokal, ikut dijalankan CI
(`build-apk.yml` dan `build-merged.yml`) sebelum APK dibangun:
- unduh baru, resume 206, 416 lokal>total, 416 lokal==total, server abaikan Range,
  body HTML, sisa file HTML, 206 dengan start salah, 403, putus di tengah,
  PackDownloader `.part` basi, dua skenario ala Drive (HTML tanpa Content-Range).

Uji ke **Drive asli** (`1mzfzP-…`, 712.800.749 B):

| Skenario | Hasil |
|---|---|
| lokal 712.800.750 B | 416 → probe `712800749` → ulang dari 0 → byte awal `Rar!\x1a\x07` ✅ |
| lokal 712.800.749 B | 416 → probe → selesai, **0 byte diunduh** (2,7 dtk) ✅ |
| resume dari 1 MB | `206`, range `1000000-712800748/712800749`, data nyambung ✅ |

## Belum

Menjalankan Wine di HP ARM64 (masih buntu yang sama: sandbox x86 tanpa Android).
