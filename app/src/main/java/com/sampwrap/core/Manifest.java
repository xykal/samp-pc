package com.sampwrap.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Manifest paket data (hashed dari paket asli "TUTOR GTA SAMP PC").
 *
 * Format per baris (tab-separated):
 *   path<TAB>size<TAB>tag<TAB>sha256<TAB>url
 *
 * Tag:
 *   core     wajib (models, data, anim, exe)
 *   sfx      efek suara umum
 *   speech   suara ped (SPC_*) — 1,81 GB, penghemat terbesar
 *   radio    13 stasiun radio — 0,89 GB
 *   cutscene video + audio cutscene — 0,38 GB
 *   ads      iklan radio + ambience
 *   optional samp/addon/cleo/modloader
 */
public final class Manifest {

    public enum Tag {
        CORE, SFX, SPEECH, RADIO, CUTSCENE, ADS, OPTIONAL
    }

    public static final class Entry {
        public final String path;
        public final long size;
        public final Tag tag;
        public final String sha256; // "-" kalau tidak diverifikasi
        public final String url;

        public Entry(String path, long size, Tag tag, String sha256, String url) {
            this.path = path;
            this.size = size;
            this.tag = tag;
            this.sha256 = sha256;
            this.url = url;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static Manifest load(String file) throws IOException {
        Manifest m = new Manifest();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\t");
                if (p.length < 3) continue;
                m.entries.add(new Entry(
                        p[0],
                        parseLong(p[1]),
                        parseTag(p[2]),
                        p.length > 3 ? p[3] : "-",
                        p.length > 4 ? p[4] : ""));
            }
        }
        return m;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public void add(Entry e) {
        entries.add(e);
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private static Tag parseTag(String s) {
        switch (s.trim().toLowerCase()) {
            case "sfx": return Tag.SFX;
            case "speech": return Tag.SPEECH;
            case "radio": return Tag.RADIO;
            case "cutscene": return Tag.CUTSCENE;
            case "ads": return Tag.ADS;
            case "optional": return Tag.OPTIONAL;
            default: return Tag.CORE;
        }
    }

    // ---------------------------------------------------------------- plan ----

    /** Nama tier yang dipakai UI & Prefs. */
    public static final String TIER_EXTREME = "extreme";
    public static final String TIER_LITE = "lite";
    public static final String TIER_FULL = "full";

    public static EnumSet<Tag> tagsFor(String tier) {
        switch (tier) {
            case TIER_FULL: return fullTags();
            case TIER_LITE: return liteTags();
            default: return extremeTags();
        }
    }

    /** EXTREME: hanya yang wajib + suara umum. */
    public static EnumSet<Tag> extremeTags() {
        return EnumSet.of(Tag.CORE, Tag.SFX, Tag.OPTIONAL);
    }

    /** LITE: EXTREME + radio. */
    public static EnumSet<Tag> liteTags() {
        return EnumSet.of(Tag.CORE, Tag.SFX, Tag.RADIO, Tag.OPTIONAL);
    }

    /** FULL: semuanya. */
    public static EnumSet<Tag> fullTags() {
        return EnumSet.allOf(Tag.class);
    }

    public long totalSize(EnumSet<Tag> included) {
        long t = 0;
        for (Entry e : entries) if (included.contains(e.tag)) t += e.size;
        return t;
    }

    public long totalSizeAll() {
        return totalSize(fullTags());
    }

    public long sizeOf(Tag tag) {
        long t = 0;
        for (Entry e : entries) if (e.tag == tag) t += e.size;
        return t;
    }

    public int countOf(Tag tag) {
        int n = 0;
        for (Entry e : entries) if (e.tag == tag) n++;
        return n;
    }

    public List<Entry> plan(EnumSet<Tag> included) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (included.contains(e.tag)) out.add(e);
        return out;
    }

    public boolean includes(EnumSet<Tag> included, String path) {
        for (Entry e : entries) {
            if (e.path.equals(path)) return included.contains(e.tag);
        }
        return true; // tidak dikenal -> anggap core, jangan dibuang
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.0f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }
}
