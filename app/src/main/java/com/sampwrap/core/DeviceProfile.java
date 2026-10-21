package com.sampwrap.core;

/**
 * Deteksi perangkat -> preset optimasi.
 *
 * GTA:SA PC di Android itu "double translation":
 *   x86 (gta_sa.exe / samp.dll) --Box64--> ARM64 --Vulkan/GL--> GPU HP
 * Jadi preset salah = 8 fps atau crash. Tiap tier di bawah ini punya
 * angka FPS jujur, bukan harapan.
 *
 * Tier:
 *   ULTRA_LOW  : PowerVR kelas Helio G36/G35 (Cortex-A53) -> target 10-20 fps
 *   SAFETY     : PowerVR/Mali kelas bawah, tanpa Vulkan memadai
 *   BALANCED   : Mali kelas menengah, Snapdragon 6xx/7xx
 *   PERFORMANCE: Adreno 6xx/7xx ke atas + Turnip
 *   ULTRA      : Adreno 7xx + RAM 8 GB+, full paket
 */
public final class DeviceProfile {

    public enum GpuFamily { ADRENO, MALI, POWERVR, UNKNOWN }

    public static final class Spec {
        public final String soc;
        public final String gpu;
        public final int ramMb;
        public final int sdk;
        public final boolean supportsVulkan;
        public final boolean is64BitHost;
        public final int cores;

        public Spec(String soc, String gpu, int ramMb, int sdk,
                    boolean supportsVulkan, boolean is64BitHost, int cores) {
            this.soc = soc;
            this.gpu = gpu;
            this.ramMb = ramMb;
            this.sdk = sdk;
            this.supportsVulkan = supportsVulkan;
            this.is64BitHost = is64BitHost;
            this.cores = cores;
        }

        /** Konstruktor lama (tanpa info core) supaya kode lama tetap jalan. */
        public Spec(String soc, String gpu, int ramMb, int sdk,
                    boolean supportsVulkan, boolean is64BitHost) {
            this(soc, gpu, ramMb, sdk, supportsVulkan, is64BitHost, 8);
        }
    }

    /** Preset yang dipakai untuk mengisi container Winlator. */
    public static final class Preset {
        public final String tier;           // ULTRA_LOW / SAFETY / BALANCED / PERFORMANCE / ULTRA
        public final String screenSize;
        public final String graphicsDriver; // turnip / virgl / zink / "-"
        public final String dxWrapper;      // dxvk / wined3d
        public final String dxvkVersion;
        public final String box64Preset;    // performance / compatibility
        public final boolean wow64;
        public final boolean esync;
        public final String audioDriver;
        public final String recommendedTier; // extreme | lite | full
        public final int fpsTarget;
        public final String drawDistance;   // ditulis ke gta_sa.set / config
        public final boolean viable;        // realistis buat main?
        public final String notes;

        Preset(String tier, String screenSize, String graphicsDriver, String dxWrapper,
               String dxvkVersion, String box64Preset, boolean wow64, boolean esync,
               String audioDriver, String recommendedTier, int fpsTarget,
               String drawDistance, boolean viable, String notes) {
            this.tier = tier;
            this.screenSize = screenSize;
            this.graphicsDriver = graphicsDriver;
            this.dxWrapper = dxWrapper;
            this.dxvkVersion = dxvkVersion;
            this.box64Preset = box64Preset;
            this.wow64 = wow64;
            this.esync = esync;
            this.audioDriver = audioDriver;
            this.recommendedTier = recommendedTier;
            this.fpsTarget = fpsTarget;
            this.drawDistance = drawDistance;
            this.viable = viable;
            this.notes = notes;
        }

        /** Baris ringkas buat ditampilkan di layar. */
        public String summary() {
            return tier + " · " + screenSize + " · " + graphicsDriver + "/" + dxWrapper
                    + " · target " + fpsTarget + " fps";
        }

        @Override
        public String toString() {
            return summary() + " · box64=" + box64Preset + " wow64=" + wow64
                    + " esync=" + esync + " pack=" + recommendedTier
                    + (viable ? "" : " [TIDAK REALISTIS]");
        }
    }

    // ------------------------------------------------------------- detection --

    public static GpuFamily gpuFamily(String gpu) {
        String g = gpu == null ? "" : gpu.toLowerCase();
        if (g.contains("adreno")) return GpuFamily.ADRENO;
        if (g.contains("mali")) return GpuFamily.MALI;
        if (g.contains("powervr") || g.contains("img ")) return GpuFamily.POWERVR;
        return GpuFamily.UNKNOWN;
    }

    /** SoC kelas sangat rendah yang perlu penanganan khusus (A53 + PowerVR). */
    public static boolean isLowEndSoc(String soc) {
        String s = soc == null ? "" : soc.toLowerCase();
        return s.contains("g36") || s.contains("g35") || s.contains("g37")
                || s.contains("helio g3") || s.contains("a53")
                || s.contains("mt6765") || s.contains("mt6762") || s.contains("mt6761");
    }

    // ---------------------------------------------------------------- presets --

    public static Preset pick(Spec s) {
        GpuFamily fam = gpuFamily(s.gpu);

        if (!s.is64BitHost) {
            return new Preset("UNSUPPORTED", "-", "-", "-", "-", "-",
                    false, false, "-", "extreme", 0, "-", false,
                    "HP 32-bit tidak bisa. Box64/Wine butuh host ARM64.");
        }

        // PowerVR kelas Helio G36/G35/G37 (Xiaomi Redmi A3, Realme C51, dll)
        boolean ultraLow = fam == GpuFamily.POWERVR && (isLowEndSoc(s.soc) || s.ramMb <= 4096);
        if (ultraLow) {
            return new Preset("ULTRA_LOW", "640x360", "virgl", "wined3d", "-",
                    "compatibility", true, false, "alsa", "extreme", 15, "0.30", false,
                    "Helio G36 (PowerVR GE8320 + Cortex-A53). GTA:SA PC + SA-MP di sini "
                            + "PRAKTIS tidak bisa main: A53 tanpa big core + PowerVR tanpa Vulkan "
                            + "yang layak = 8-15 fps, loading 5+ menit. App tetap nyiapin preset ini, "
                            + "tapi buat main harian pakai client native mobile atau streaming.");
        }

        if (!s.supportsVulkan) {
            return new Preset("SAFETY", "640x360", "virgl", "wined3d", "-",
                    "compatibility", true, false, "alsa", "extreme", 12, "0.25", false,
                    "Tidak ada Vulkan 1.1+. DXVK mati, jatuh ke WineD3D+GL. "
                            + "Bisa nyala, tapi jangan dipakai main serius.");
        }

        if (fam == GpuFamily.POWERVR) {
            return new Preset("SAFETY", "720x404", "virgl", "wined3d", "-",
                    "compatibility", true, false, "alsa", "extreme", 18, "0.30", false,
                    "PowerVR (Pixel/Tensor atau Helio kelas bawah). Dukungan minim, sering crash.");
        }

        if (s.ramMb < 4096) {
            return new Preset("SAFETY", "640x360", "virgl", "wined3d", "-",
                    "compatibility", true, false, "alsa", "extreme", 15, "0.25", false,
                    "RAM < 4 GB. Pakai Lite Extreme, tutup semua app lain.");
        }

        switch (fam) {
            case ADRENO:
                if (adrenoClass(s.gpu) >= 700 && s.ramMb >= 8192) {
                    return new Preset("ULTRA", "1280x720", "turnip", "dxvk", "2.4+",
                            "performance", true, true, "alsa", "full", 30, "0.70", true,
                            "Adreno 7xx/8xx + RAM 8 GB+. Turnip + DXVK 2.4, lock 30 fps "
                                    + "(physics GTA:SA dirancang untuk 25-30 fps).");
                }
                return new Preset("PERFORMANCE", "960x540", "turnip", "dxvk", "2.4+",
                        "performance", true, true, "alsa", "lite", 30, "0.50", true,
                        "Adreno + RAM 6-8 GB. Lancar buat SA-MP, turunkan draw distance "
                                + "kalau di area ramai.");

            case MALI:
                return new Preset("BALANCED", "854x480", "zink", "dxvk", "1.10.3",
                        "compatibility", true, false, "alsa", "lite", 24, "0.40", true,
                        "Mali kelas menengah (Helio G88/G99, Exynos). Pakai Zink, "
                                + "jangan kaget fps drop di kota.");

            default:
                return new Preset("SAFETY", "800x450", "virgl", "dvxk_fallback", "1.10.3",
                        "compatibility", true, false, "alsa", "extreme", 18, "0.30", false,
                        "GPU tidak dikenali. Mulai dari preset paling aman.");
        }
    }

    /**
     * Ambil angka kelas Adreno dari nama GPU: "Adreno 740" -> 740,
     * "Adreno 642L" -> 642. 0 kalau tidak terbaca.
     */
    public static int adrenoClass(String gpu) {
        if (gpu == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("adreno\\s*(\\d{3})", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(gpu);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pesan singkat buat ditampilkan di UI (bukan log). */
    public static String verdict(Spec s, Preset p) {
        if (!p.viable) {
            return "Perangkat ini di bawah batas realistis untuk GTA:SA PC.\n\n"
                    + p.notes + "\n\n"
                    + "Saran: pakai client SA-MP Android native (ringan, bisa di HP ini) "
                    + "atau streaming dari PC.";
        }
        return "Siap. " + p.notes;
    }
}
