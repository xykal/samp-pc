package com.sampwrap.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Installer mod untuk folder game GTA:SA PC di dalam container.
 *
 * Yang dikenali:
 *   .asi              -> root folder game (butuh ASI Loader, biasanya sudah ada di SA-MP)
 *   .cs / .cm         -> CLEO/
 *   .lua              -> moonloader/
 *   .dll              -> modloader/<nama-mod>/  (plugin modloader)
 *   .txd/.dff/.img    -> modloader/<nama-mod>/  (JANGAN menimpa gta3.img langsung)
 *   .txt (CLEO)       -> CLEO/
 *
 * Semua file yang bakal menimpa file lama dibackup ke mods/_backup/<timestamp>/.
 */
public final class ModInstaller {

    public enum Kind { ASI, CLEO, MOONLOADER, MODLOADER, SKIN_OR_MODEL, UNKNOWN }

    public static final class Installed {
        public final File src;
        public final String target;   // path relatif terhadap folder game
        public final Kind kind;
        public final boolean backedUp;

        Installed(File src, String target, Kind kind, boolean backedUp) {
            this.src = src;
            this.target = target;
            this.kind = kind;
            this.backedUp = backedUp;
        }

        @Override
        public String toString() {
            return String.format("%-22s %-14s %s%s", src.getName(), kind, target,
                    backedUp ? "  (backup)" : "");
        }
    }

    private final File gameDir;

    public ModInstaller(File gameDir) {
        this.gameDir = gameDir;
    }

    /** Klasifikasi file berdasarkan nama & tempat asal (mis. dari folder "CLEO" di zip mod). */
    public static Kind classify(String fileName, String sourcePath) {
        String n = fileName.toLowerCase();
        String p = sourcePath == null ? "" : sourcePath.toLowerCase();
        if (n.endsWith(".asi")) return Kind.ASI;
        if (n.endsWith(".cs") || n.endsWith(".cm")) return Kind.CLEO;
        if (n.endsWith(".lua")) return Kind.MOONLOADER;
        if (n.endsWith(".dll")) return Kind.MODLOADER;
        if (n.endsWith(".txd") || n.endsWith(".dff") || n.endsWith(".img")) return Kind.SKIN_OR_MODEL;
        if (n.endsWith(".txt") && (p.contains("cleo") || p.contains("script"))) return Kind.CLEO;
        if (p.contains("modloader")) return Kind.MODLOADER;
        if (p.contains("cleo")) return Kind.CLEO;
        return Kind.UNKNOWN;
    }

    /**
     * @param files     file mod yang mau dipasang
     * @param modName   nama folder mod (dipakai untuk modloader/<modName>/)
     */
    public List<Installed> install(List<File> files, String modName, boolean backup) throws IOException {
        List<Installed> out = new ArrayList<>();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backupRoot = new File(gameDir, "mods/_backup/" + ts);

        for (File f : files) {
            Kind kind = classify(f.getName(), f.getParent());
            String rel;
            switch (kind) {
                case ASI:            rel = f.getName(); break;
                case CLEO:           rel = "CLEO/" + f.getName(); break;
                case MOONLOADER:     rel = "moonloader/" + f.getName(); break;
                case MODLOADER:      rel = "modloader/" + modName + "/" + f.getName(); break;
                case SKIN_OR_MODEL:  rel = "modloader/" + modName + "/" + f.getName(); break;
                default:             rel = "modloader/" + modName + "/" + f.getName(); break;
            }

            File target = new File(gameDir, rel);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Tidak bisa membuat folder: " + parent);
            }

            boolean didBackup = false;
            if (target.exists() && backup) {
                File b = new File(backupRoot, rel);
                File bp = b.getParentFile();
                if (bp != null && !bp.exists()) bp.mkdirs();
                Files.copy(target.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING);
                didBackup = true;
            }

            Files.copy(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            out.add(new Installed(f, rel, kind, didBackup));
        }

        writeLog(out, modName);
        return out;
    }

    private void writeLog(List<Installed> list, String modName) throws IOException {
        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists() && !modsDir.mkdirs()) return;
        File log = new File(modsDir, "installed.log");
        StringBuilder sb = new StringBuilder();
        sb.append("# install ").append(modName).append(" ")
          .append(LocalDateTime.now()).append('\n');
        for (Installed i : list) {
            sb.append(i.kind).append('\t').append(i.target).append('\n');
        }
        Files.write(log.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    /** Daftar mod yang terpasang: folder di modloader/ (selain _backup). */
    public List<String> listMods() {
        List<String> out = new ArrayList<>();
        File ml = new File(gameDir, "modloader");
        File[] dirs = ml.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.getName().startsWith("_")) out.add("modloader: " + d.getName());
            }
        }
        File asi = new File(gameDir, "CLEO");
        File[] cleo = asi.listFiles();
        if (cleo != null) {
            int n = 0;
            for (File f : cleo) if (f.getName().toLowerCase().endsWith(".cs")) n++;
            if (n > 0) out.add("CLEO: " + n + " script");
        }
        return out;
    }
}
