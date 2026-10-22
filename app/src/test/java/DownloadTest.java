import com.sampwrap.core.ResumableDownload;
import com.sampwrap.core.ResumePolicy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test unduhan tahan-putus untuk ResumableDownload + ResumePolicy.
 * Tanpa Android, tanpa jaringan luar — server HTTP lokal.
 *
 * Jalankan:
 *   javac -d out $(find app/src/main/java/com/sampwrap/core -name '*.java') \
 *         app/src/test/java/DownloadTest.java
 *   java -cp out DownloadTest
 */
public class DownloadTest {

    static int failed = 0;

    static void check(String label, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + label);
        if (!ok) failed++;
    }

    // ---------------------------------------------------------------- server --

    static byte[] payload(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i * 31 + 7);
        return b;
    }

    static final AtomicInteger requests = new AtomicInteger();

    static HttpServer start(final byte[] body) throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // /.file  — patuh Range (200 / 206 / 416)
        srv.createContext("/file", ex -> {
            requests.incrementAndGet();
            String range = ex.getRequestHeaders().getFirst("Range");
            long start = parseStart(range);
            if (start >= body.length) {
                ex.getResponseHeaders().add("Content-Range", "bytes */" + body.length);
                ex.sendResponseHeaders(416, -1);
                ex.close();
                return;
            }
            if (start > 0) {
                long end = body.length - 1L;
                ex.getResponseHeaders().add("Content-Range",
                        "bytes " + start + "-" + end + "/" + body.length);
                ex.sendResponseHeaders(206, body.length - start);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body, (int) start, (int) (body.length - start));
                }
            } else {
                ex.getResponseHeaders().add("Accept-Ranges", "bytes");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
        });

        // /.norange — selalu 200 penuh, Range diabaikan
        srv.createContext("/norange", ex -> {
            requests.incrementAndGet();
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });

        // /.html — server kirim halaman, bukan file (kuota Drive habis)
        srv.createContext("/html", ex -> {
            requests.incrementAndGet();
            byte[] html = "<!DOCTYPE html><html><body>Quota exceeded for this file</body></html>"
                    .getBytes("UTF-8");
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        });

        // /.bad206 — balas 206 tapi Content-Range mulai di 0 (server ngawur)
        srv.createContext("/bad206", ex -> {
            requests.incrementAndGet();
            ex.getResponseHeaders().add("Content-Range", "bytes 0-" + (body.length - 1) + "/" + body.length);
            ex.sendResponseHeaders(206, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });

        // /.drive416 — meniru Google Drive: 416 dibalas HTML, TANPA Content-Range
        srv.createContext("/drive416", ex -> {
            requests.incrementAndGet();
            String range = ex.getRequestHeaders().getFirst("Range");
            long start = parseStart(range);
            boolean oneByte = "bytes=0-0".equals(range);
            if (start >= body.length) {
                byte[] html = ("<html><body>Error 416 (Bad Request)!!1</body></html>")
                        .getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(416, html.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(html);
                }
                return;
            }
            if (oneByte) {
                ex.getResponseHeaders().add("Content-Range", "bytes 0-0/" + body.length);
                ex.sendResponseHeaders(206, 1);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body, 0, 1);
                }
                return;
            }
            if (start > 0) {
                ex.getResponseHeaders().add("Content-Range",
                        "bytes " + start + "-" + (body.length - 1) + "/" + body.length);
                ex.sendResponseHeaders(206, body.length - start);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body, (int) start, (int) (body.length - start));
                }
                return;
            }
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });

        // /.forbidden — 403 seperti kuota Drive habis
        srv.createContext("/forbidden", ex -> {
            requests.incrementAndGet();
            byte[] msg = "forbidden".getBytes("UTF-8");
            ex.sendResponseHeaders(403, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
        });

        srv.start();
        return srv;
    }

    static long parseStart(String range) {
        if (range == null || !range.startsWith("bytes=")) return 0;
        String s = range.substring(6);
        int dash = s.indexOf('-');
        if (dash <= 0) return 0;
        try {
            return Long.parseLong(s.substring(0, dash));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String base(HttpServer srv) {
        return "http://127.0.0.1:" + srv.getAddress().getPort();
    }

    // ------------------------------------------------------------------ main --

    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("dl").toFile();

        System.out.println("\n[1] ResumePolicy — parsing Content-Range");
        check("total dari 'bytes 100-199/200' = 200",
                ResumePolicy.totalFromContentRange("bytes 100-199/200") == 200);
        check("total dari 'bytes */500' = 500",
                ResumePolicy.totalFromContentRange("bytes */500") == 500);
        check("total tanpa slash = -1",
                ResumePolicy.totalFromContentRange("bytes 0-99") == -1);
        check("start dari 'bytes 123-199/200' = 123",
                ResumePolicy.startFromContentRange("bytes 123-199/200") == 123);
        check("start dari 'bytes */200' = -1",
                ResumePolicy.startFromContentRange("bytes */200") == -1);
        check("416 + total sama dengan lokal -> COMPLETE",
                ResumePolicy.decide(500, 416, "bytes */500", -1) == ResumePolicy.Action.COMPLETE);
        check("416 + lokal lebih besar -> RESTART",
                ResumePolicy.decide(900, 416, "bytes */500", -1) == ResumePolicy.Action.RESTART);
        check("416 tanpa Content-Range -> RESTART (jangan tebak selesai)",
                ResumePolicy.decide(900, 416, null, -1) == ResumePolicy.Action.RESTART);
        check("200 -> RESTART (Range diabaikan server)",
                ResumePolicy.decide(400, 200, null, 1000) == ResumePolicy.Action.RESTART);
        check("206 + start cocok -> APPEND",
                ResumePolicy.decide(400, 206, "bytes 400-999/1000", 600)
                        == ResumePolicy.Action.APPEND);

        byte[] body = payload(2_000_00);   // ~200 KB
        HttpServer srv = start(body);
        File dst = new File(tmp, "pack.download");

        // ---------------------------------------------------------- 2. tanpa resume
        System.out.println("\n[2] Unduh baru (HTTP 200)");
        requests.set(0);
        final StringBuilder log = new StringBuilder();
        long n = ResumableDownload.fetch(dst, start -> conn(base(srv) + "/file", start), 3,
                listener(log));
        check("ukuran benar (" + n + ")", n == body.length && dst.length() == body.length);
        check("isi identik", same(dst, body));

        // ------------------------------------------------------ 3. resume normal
        System.out.println("\n[3] Resume (206 lanjutan)");
        byte[] part = new byte[120_000];
        System.arraycopy(body, 0, part, 0, part.length);
        Files.write(dst.toPath(), part);
        log.setLength(0);
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/file", start), 3, listener(log));
        check("lanjut dari 120000 byte, hasil identik", same(dst, body));
        check("log menyebut range 206", log.toString().contains("206"));

        // --------------------------------------------- 4. 416: file sementara basi
        System.out.println("\n[4] 416 karena file sementara lebih besar dari file asli");
        byte[] stale = payload(300_000);          // lebih besar dari body (200.000)
        Files.write(dst.toPath(), stale);
        log.setLength(0);
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/file", start), 3, listener(log));
        check("file diulang dari nol, isi benar", same(dst, body));
        check("log menyebut 416", log.toString().contains("416"));

        // ------------------------------------------ 5. 416 tapi lokal == total: selesai
        System.out.println("\n[5] 416 dengan ukuran lokal == total -> tidak unduh ulang");
        Files.write(dst.toPath(), body);
        requests.set(0);
        final StringBuilder log5 = new StringBuilder();
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/file", start), 3,
                listener(log5));
        check("isi tetap benar", same(dst, body));
        check("hanya 1 request (tidak mengunduh ulang)", requests.get() == 1);

        // ------------------------------------------ 6. server mengabaikan Range (200)
        System.out.println("\n[6] Server mengabaikan Range (selalu 200)");
        byte[] part2 = new byte[50_000];
        System.arraycopy(body, 0, part2, 0, part2.length);
        Files.write(dst.toPath(), part2);
        log.setLength(0);
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/norange", start), 3,
                listener(log));
        check("tidak dobel (ukuran benar)", dst.length() == body.length);
        check("isi identik", same(dst, body));

        // ------------------------------------------- 7. server kirim HTML di body
        System.out.println("\n[7] Server kirim halaman HTML, bukan file");
        dst.delete();
        String err = null;
        try {
            ResumableDownload.fetch(dst, start -> conn(base(srv) + "/html", start), 1,
                    listener(log));
        } catch (Exception e) {
            err = e.getMessage();
        }
        check("gagal dengan pesan HTML (" + err + ")",
                err != null && err.toLowerCase().contains("html"));

        // -------------------------------- 8. sisa file lama berisi HTML -> dibuang
        System.out.println("\n[8] Sisa file sementara berisi HTML -> dibuang, unduh bersih");
        Files.write(dst.toPath(),
                "<!DOCTYPE html><html>quota</html>".getBytes("UTF-8"));
        log.setLength(0);
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/file", start), 3,
                listener(log));
        check("isi akhir benar", same(dst, body));
        check("log menyebut HTML dibuang", log.toString().toLowerCase().contains("html"));

        // -------------------------------------- 9. 206 dengan start tidak cocok
        System.out.println("\n[9] 206 tapi Content-Range mulai di 0 (server ngawur)");
        byte[] part3 = new byte[30_000];
        Files.write(dst.toPath(), part3);
        log.setLength(0);
        ResumableDownload.fetch(dst, start -> conn(base(srv) + "/bad206", start), 3,
                listener(log));
        check("diulang dari nol, isi benar", same(dst, body));

        // --------------------------------------------------- 10. 403 -> pesan jelas
        System.out.println("\n[10] HTTP 403 -> pesan error, bukan file kosong");
        dst.delete();
        err = null;
        try {
            ResumableDownload.fetch(dst, start -> conn(base(srv) + "/forbidden", start), 1,
                    listener(log));
        } catch (Exception e) {
            err = e.getMessage();
        }
        check("gagal dengan pesan 403 (" + err + ")",
                err != null && err.contains("403"));

        // ------------------------------------- 11. koneksi putus di tengah -> lanjut
        System.out.println("\n[11] Koneksi putus di tengah -> retry melanjutkan (bukan nol)");
        dst.delete();
        HttpServer flaky = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger hits = new AtomicInteger();
        flaky.createContext("/flaky", ex -> {
            int hit = hits.incrementAndGet();
            String range = ex.getRequestHeaders().getFirst("Range");
            long start = parseStart(range);
            ex.getResponseHeaders().add("Content-Range",
                    "bytes " + start + "-" + (body.length - 1) + "/" + body.length);
            long len = body.length - start;
            if (hit == 1) {
                // kirim separuh lalu tutup koneksi
                ex.sendResponseHeaders(206, len);
                OutputStream os = ex.getResponseBody();
                os.write(body, (int) start, (int) (len / 2));
                os.flush();
                ex.getHttpContext();
                os.close();
                return;
            }
            ex.sendResponseHeaders(206, len);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body, (int) start, (int) len);
            }
        });
        flaky.start();
        final HttpServer f2 = flaky;
        try {
            ResumableDownload.fetch(dst, start -> conn(base(f2) + "/flaky", start), 4,
                    listener(log));
        } catch (Exception e) {
            System.out.println("    (catatan: " + e.getMessage() + ")");
        }
        check("akhirnya utuh setelah retry", dst.length() == body.length && same(dst, body));

        // --------------------------- 12. PackDownloader (mode per-file) juga pulih
        System.out.println("\n[12] PackDownloader: .part basi (lebih besar) -> restart, sha cocok");
        File dlDir = new File(tmp, "perfile");
        dlDir.mkdirs();
        File target = new File(dlDir, "models/gta3.img.part");
        target.getParentFile().mkdirs();
        Files.write(target.toPath(), payload(body.length + 50_000));   // basi & kebesaran

        com.sampwrap.core.Manifest mf = new com.sampwrap.core.Manifest();
        String sha = sha256(body);
        mf.add(new com.sampwrap.core.Manifest.Entry("models/gta3.img", body.length,
                com.sampwrap.core.Manifest.Tag.CORE, sha, base(srv) + "/file"));
        com.sampwrap.core.PackDownloader pd = new com.sampwrap.core.PackDownloader(
                mf, java.util.EnumSet.of(com.sampwrap.core.Manifest.Tag.CORE), dlDir, null);
        com.sampwrap.core.PackDownloader.Stats st = pd.run();
        File got = new File(dlDir, "models/gta3.img");
        check("file jadi & benar (" + st.bytes + " B)", got.isFile() && same(got, body));

        // ------------------- 13. ala Google Drive: 416 tanpa Content-Range, file LENGKAP
        System.out.println("\n[13] Drive: 416 tanpa Content-Range, file lokal == total -> tidak unduh ulang");
        Files.write(dst.toPath(), body);
        requests.set(0);
        final StringBuilder log13 = new StringBuilder();
        long got13 = ResumableDownload.fetch(dst, driveSource(base(srv), log13), 3,
                listener(log13));
        check("dianggap selesai tanpa unduh isi (" + got13 + ")", got13 == body.length);
        check("tanya ukuran lewat probe (2 request)", requests.get() == 2);
        check("log menyebut ukuran server", log13.toString().contains("ukuran file di server"));

        // ------------------- 14. ala Google Drive: 416 karena file lokal kebesaran
        System.out.println("\n[14] Drive: 416 karena file lokal lebih besar -> probe total, ulang dari 0");
        Files.write(dst.toPath(), payload(body.length + 7_000));
        log13.setLength(0);
        ResumableDownload.fetch(dst, driveSource(base(srv), log13), 3, listener(log13));
        check("isi akhir benar", same(dst, body));
        check("log menyebut 416 lalu unduh ulang",
                log13.toString().contains("416")
                        && log13.toString().contains("selesai " + body.length + " byte"));

        srv.stop(0);
        flaky.stop(0);

        System.out.println(failed == 0
                ? "\nSEMUA TEST LULUS"
                : "\n" + failed + " TEST GAGAL");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Source yang meniru DriveDownloader.probeTotal: ukuran lewat Range 0-0. */
    static ResumableDownload.Source driveSource(final String base, final StringBuilder sb) {
        return new ResumableDownload.Source() {
            @Override public HttpURLConnection open(long start) throws IOException {
                return conn(base + "/drive416", start);
            }

            @Override public long knownTotal() throws IOException {
                HttpURLConnection c = (HttpURLConnection)
                        new URL(base + "/drive416").openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("Range", "bytes=0-0");
                try {
                    int code = c.getResponseCode();
                    if (code == 206) {
                        return ResumePolicy.totalFromContentRange(
                                c.getHeaderField("Content-Range"));
                    }
                    if (code == 200) {
                        long len = c.getContentLengthLong();
                        return len > 0 ? len : -1;
                    }
                    return -1;
                } finally {
                    c.disconnect();
                }
            }
        };
    }

    static ResumableDownload.Listener listener(final StringBuilder sb) {
        return new ResumableDownload.Listener() {
            @Override public void onLog(String m) {
                sb.append(m).append('\n');
                System.out.println("    | " + m);
            }

            @Override public void onProgress(long done, long total) { }
        };
    }

    static HttpURLConnection conn(String url, long start) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        if (start > 0) c.setRequestProperty("Range", "bytes=" + start + "-");
        return c;
    }

    static String sha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "-";
        }
    }

    static boolean same(File f, byte[] expect) throws IOException {
        if (f.length() != expect.length) return false;
        byte[] got = Files.readAllBytes(f.toPath());
        for (int i = 0; i < expect.length; i++) if (got[i] != expect[i]) return false;
        return true;
    }
}
