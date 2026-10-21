import com.sampwrap.core.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test harness core wrapper — TANPA Android SDK.
 * Jalankan:  javac -d out $(find core/src -name '*.java') core/test/CoreTest.java
 *            java -cp out CoreTest
 */
public class CoreTest {

    static int failed = 0;

    static void check(String label, boolean cond) {
        System.out.println((cond ? "  PASS  " : "  FAIL  ") + label);
        if (!cond) failed++;
    }

    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("sampwrap").toFile();

        // ---------------------------------------------------------- 1. manifest
        System.out.println("\n[1] Manifest Lite vs Full");
        File mf = new File(tmp, "manifest.tsv");
        Files.writeString(mf.toPath(), String.join("\n",
                "# path\tsize\ttag\tsha256\turl",
                "gta_sa.exe\t15000000\tcore\t-\t-",
                "models/gta3.img\t912000000\tcore\t-\t-",
                "audio/SFX/GENRL\t180000000\tsfx\t-\t-",
                "audio/streams/PLAYBACK.ogg\t150000000\tradio\t-\t-",
                "audio/SFX/SPC_GA\t900000000\tspeech\t-\t-",
                "audio/streams/ADVERTS\t40000000\tads\t-\t-",
                "audio/streams/KROSE.ogg\t160000000\tradio\t-\t-",
                "data/cutscene/cuts.img\t240000000\tcutscene\t-\t-",
                "") + "\n");

        Manifest m = Manifest.load(mf.getAbsolutePath());
        long full = m.totalSize(Manifest.fullTags());
        long lite = m.totalSize(Manifest.liteTags());
        long extreme = m.totalSize(Manifest.extremeTags());

        System.out.println("     full    = " + Manifest.humanSize(full));
        System.out.println("     lite    = " + Manifest.humanSize(lite)
                + "  (hemat " + pct(lite, full) + ")");
        System.out.println("     extreme = " + Manifest.humanSize(extreme)
                + "  (hemat " + pct(extreme, full) + ")");
        check("lite lebih kecil dari full", lite < full);
        check("radio terdeteksi", m.sizeOf(Manifest.Tag.RADIO) == 310_000_000L);
        check("plan lite memuat radio", m.plan(Manifest.liteTags()).stream()
                .anyMatch(e -> e.tag == Manifest.Tag.RADIO));
        check("plan lite TIDAK memuat speech", m.plan(Manifest.liteTags()).stream()
                .noneMatch(e -> e.tag == Manifest.Tag.SPEECH));
        check("plan full memuat speech", m.plan(Manifest.fullTags()).stream()
                .anyMatch(e -> e.tag == Manifest.Tag.SPEECH));
        check("plan extreme tanpa speech/radio/cutscene",
                m.plan(Manifest.extremeTags()).stream().noneMatch(e ->
                        e.tag == Manifest.Tag.SPEECH || e.tag == Manifest.Tag.RADIO
                                || e.tag == Manifest.Tag.CUTSCENE));

        // -------------------------------------------------------- 2. pack install
        System.out.println("\n[2] Install paket data (zip) dengan plan lite");
        File zip = new File(tmp, "pack.zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            put(z, "gta_sa.exe", "fake-exe");
            put(z, "models/gta3.img", "fake-img-data");
            put(z, "audio/streams/PLAYBACK.ogg", "radio-data");
            put(z, "data/cutscene/cuts.img", "cutscene-data");
            put(z, "../../evil.txt", "zip-slip");   // harus ditolak
        }
        File game = new File(tmp, "game");
        game.mkdirs();

        PackInstaller inst = new PackInstaller(m, Manifest.extremeTags(), null);
        PackInstaller.Result r = inst.install(zip, game, false);
        System.out.println("     hasil: " + r);

        check("gta_sa.exe terpasang", new File(game, "gta_sa.exe").exists());
        check("models terpasang", new File(game, "models/gta3.img").exists());
        check("radio DILEWATI (tier extreme)",
                !new File(game, "audio/streams/PLAYBACK.ogg").exists());
        check("speech DILEWATI (tier extreme)",
                !new File(game, "audio/SFX/SPC_GA").exists());
        check("cutscene DILEWATI", !new File(game, "data/cutscene/cuts.img").exists());
        check("zip-slip ditolak", !new File(tmp.getParentFile(), "evil.txt").exists());

        // ------------------------------------------------------- 3. mod installer
        System.out.println("\n[3] Mod installer");
        File modSrc = new File(tmp, "mod_src");
        modSrc.mkdirs();
        File asi = new File(modSrc, "sampgui.asi");
        File cs = new File(modSrc, "speedo.cs");
        File txd = new File(modSrc, "infernus.txd");
        Files.writeString(asi.toPath(), "asi");
        Files.writeString(cs.toPath(), "cleo");
        Files.writeString(txd.toPath(), "txd");

        ModInstaller mi = new ModInstaller(game);
        List<ModInstaller.Installed> installed =
                mi.install(List.of(asi, cs, txd), "SpeedoPack", true);
        for (ModInstaller.Installed i : installed) System.out.println("     " + i);

        check("asi ke root", new File(game, "sampgui.asi").exists());
        check("cs ke CLEO/", new File(game, "CLEO/speedo.cs").exists());
        check("txd ke modloader/<mod>/", new File(game, "modloader/SpeedoPack/infernus.txd").exists());

        // install ulang -> harus bikin backup
        List<ModInstaller.Installed> again =
                mi.install(List.of(asi), "SpeedoPack", true);
        check("file lama dibackup", again.get(0).backedUp);
        check("daftar mod kebaca", !mi.listMods().isEmpty());

        // ------------------------------------------------------- 4. device profile
        System.out.println("\n[4] Preset perangkat");
        DeviceProfile.Spec sd8 = new DeviceProfile.Spec("Snapdragon 8 Gen 2", "Adreno 740",
                12288, 34, true, true, 8);
        DeviceProfile.Spec sd7 = new DeviceProfile.Spec("Snapdragon 778G", "Adreno 642L",
                8192, 33, true, true, 8);
        DeviceProfile.Spec helio = new DeviceProfile.Spec("Helio G99", "Mali-G57",
                6144, 33, true, true, 8);
        DeviceProfile.Spec g36 = new DeviceProfile.Spec("Helio G36", "PowerVR GE8320",
                4096, 33, true, true, 8);
        DeviceProfile.Spec pixel = new DeviceProfile.Spec("Tensor G3", "PowerVR DXT",
                8192, 34, true, true, 9);
        DeviceProfile.Spec old32 = new DeviceProfile.Spec("Snapdragon 425", "Adreno 308",
                2048, 26, true, false, 4);
        DeviceProfile.Spec noVk = new DeviceProfile.Spec("unknown", "Adreno 610",
                4096, 30, false, true, 8);

        for (DeviceProfile.Spec s : List.of(sd8, sd7, helio, g36, pixel, old32, noVk)) {
            DeviceProfile.Preset p = DeviceProfile.pick(s);
            System.out.printf("     %-22s %-12s -> %s%n", s.soc, s.gpu, p);
            System.out.println("     " + " ".repeat(20) + "   catatan: " + p.notes);
        }
        check("Adreno 8GB dapat ULTRA/turnip", DeviceProfile.pick(sd8).tier.equals("ULTRA")
                && DeviceProfile.pick(sd8).graphicsDriver.equals("turnip"));
        check("Adreno 6-8GB dapat PERFORMANCE", DeviceProfile.pick(sd7).tier.equals("PERFORMANCE"));
        check("Mali dapat BALANCED/zink", DeviceProfile.pick(helio).graphicsDriver.equals("zink"));
        check("Helio G36 dapat ULTRA_LOW + wined3d",
                DeviceProfile.pick(g36).tier.equals("ULTRA_LOW")
                        && DeviceProfile.pick(g36).dxWrapper.equals("wined3d"));
        check("Helio G36 ditandai tidak realistis", !DeviceProfile.pick(g36).viable);
        check("32-bit ditolak", DeviceProfile.pick(old32).tier.equals("UNSUPPORTED"));
        check("tanpa vulkan -> SAFETY", DeviceProfile.pick(noVk).tier.equals("SAFETY"));

        // ------------------------------------------------------ 5. container preset
        System.out.println("\n[5] Preset container (untuk file .container Winlator)");
        ContainerPreset cp = ContainerPreset.forDevice("SAMP PC",
                DeviceProfile.pick(sd8), "/storage/emulated/0/Games/GTA-SA");
        String json = cp.toContainerJson(1);
        System.out.println(indent(json));

        check("container punya wow64 (gta_sa 32-bit)", json.contains("\"wow64Mode\": true"));
        check("container punya drive game", json.contains("/storage/emulated/0/Games/GTA-SA"));
        check("container batasi 30 fps", json.contains("maxFrameRate = 30"));

        System.out.println("\n[5b] Container preset untuk Helio G36 (tier ULTRA_LOW)");
        ContainerPreset g36c = ContainerPreset.forDevice("SAMP PC",
                DeviceProfile.pick(g36), "/storage/emulated/0/Games/GTA-SA");
        String g36json = g36c.toContainerJson(2);
        System.out.println(indent(g36json));
        check("G36 pakai wined3d", g36json.contains("\"dxwrapper\": \"wined3d\""));
        check("G36 resolusi 640x360", g36json.contains("640x360"));
        check("G36 Box64 konservatif", g36json.contains("BOX64_DYNAREC_BIGBLOCK=0"));
        check("G36 FPS tampil", g36json.contains("\"showFPS\": true"));

        // ---------------------------------------------------------------- hasil
        System.out.println("\n====================================");
        System.out.println(failed == 0 ? "SEMUA TEST LULUS" : failed + " TEST GAGAL");
        System.out.println("workdir: " + tmp);
        if (failed != 0) System.exit(1);
    }

    static String pct(long part, long whole) {
        return String.format("%.0f%%", 100.0 * (whole - part) / whole);
    }

    static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) sb.append("     ").append(line).append('\n');
        return sb.toString();
    }

    static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
