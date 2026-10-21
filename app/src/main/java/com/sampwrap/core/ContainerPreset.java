package com.sampwrap.core;

/**
 * Menghasilkan isi file container Winlator (.container, JSON) yang sudah
 * disetel untuk GTA:SA PC + SA-MP, sesuai tier perangkat.
 *
 * Nama field bisa berbeda antar fork — cek dulu di fork yang kamu pakai:
 *   app/src/main/java/com/winlator/container/Container.java
 *
 * Ditulis ke: /data/data/<pkg>/files/imagefs/home/xuser-<id>/.container
 */
public final class ContainerPreset {

    public final String name;
    public final String screenSize;
    public final String graphicsDriver;
    public final String dxWrapper;
    public final String dxWrapperConfig;
    public final String audioDriver;
    public final String box64Preset;
    public final boolean wow64Mode;
    public final boolean showFps;
    public final String drives;
    public final String envVars;
    public final int fpsTarget;

    private ContainerPreset(Builder b) {
        this.name = b.name;
        this.screenSize = b.screenSize;
        this.graphicsDriver = b.graphicsDriver;
        this.dxWrapper = b.dxWrapper;
        this.dxWrapperConfig = b.dxWrapperConfig;
        this.audioDriver = b.audioDriver;
        this.box64Preset = b.box64Preset;
        this.wow64Mode = b.wow64Mode;
        this.showFps = b.showFps;
        this.drives = b.drives;
        this.envVars = b.envVars;
        this.fpsTarget = b.fpsTarget;
    }

    /** Preset container dari rekomendasi perangkat + folder game di storage. */
    public static ContainerPreset forDevice(String appName, DeviceProfile.Preset p,
                                           String gameFolderOnStorage) {
        boolean dxvk = p.dxWrapper.equals("dxvk");

        return new Builder(appName)
                .screenSize(p.screenSize)
                .graphicsDriver(p.graphicsDriver)
                .dxWrapper(p.dxWrapper)
                .fpsTarget(p.fpsTarget)
                .dxWrapperConfig(dxvk ? dxvkConfig(p.fpsTarget) : wined3dConfig(p.tier))
                .audioDriver(p.audioDriver)
                .box64Preset(p.box64Preset)
                .wow64Mode(true)   // gta_sa.exe itu 32-bit
                .showFps(p.tier.equals("ULTRA_LOW") || p.tier.equals("SAFETY"))
                .drives("D:" + gameFolderOnStorage)
                .envVars(envFor(p))
                .build();
    }

    /**
     * DXVK: batasi frame + matikan vsync. 30 fps adalah angka aman karena
     * physics RenderWare GTA:SA dirancang untuk 25-30 fps; di atas itu motor
     * dan mobil mulai berperilaku aneh.
     */
    private static String dxvkConfig(int fps) {
        return "dxgi.maxFrameRate = " + fps + "\n"
                + "dxgi.syncInterval = 0\n"
                + "d3d9.maxFrameRate = " + fps + "\n"
                + "d3d9.presentInterval = 0\n";
    }

    private static String wined3dConfig(String tier) {
        // WineD3D ke OpenGL. Di PowerVR kelas G36 ini satu-satunya jalan.
        return "# WineD3D (tanpa DXVK/Vulkan)\n"
                + "WINEDLLOVERRIDES=d3d9=n,b\n"
                + "STAGING_SHARED_MEMORY=1\n"
                + "# tier: " + tier + "\n";
    }

    /** Env variable per tier. Box64 di Cortex-A53 butuh setting konservatif. */
    private static String envFor(DeviceProfile.Preset p) {
        StringBuilder sb = new StringBuilder();
        sb.append("MESA_GL_VERSION_OVERRIDE=4.5;")
          .append("MESA_GLSL_VERSION_OVERRIDE=450;")
          .append("BOX64_NOBANNER=1;")
          .append("BOX86_NOBANNER=1");

        switch (p.tier) {
            case "ULTRA_LOW":
            case "SAFETY":
                // A53 tanpa big core: matikan optimasi yang boros memori
                sb.append(";BOX64_DYNAREC_BIGBLOCK=0")
                  .append(";BOX64_DYNAREC_STRONGMEM=2")
                  .append(";BOX64_DYNAREC_SAFEFLAGS=2")
                  .append(";BOX64_DYNAREC_CALLRET=0")
                  .append(";BOX64_DYNAREC_FASTNAN=0")
                  .append(";BOX64_DYNAREC_FASTROUND=0")
                  .append(";BOX64_AVX=0")
                  .append(";BOX64_SSE42=0")
                  .append(";WINEDEBUG=-all");
                break;
            case "BALANCED":
                sb.append(";BOX64_DYNAREC_BIGBLOCK=1")
                  .append(";BOX64_DYNAREC_STRONGMEM=1")
                  .append(";BOX64_DYNAREC_SAFEFLAGS=1")
                  .append(";WINEDEBUG=-all");
                break;
            default: // PERFORMANCE / ULTRA
                sb.append(";BOX64_DYNAREC_BIGBLOCK=3")
                  .append(";BOX64_DYNAREC_STRONGMEM=0")
                  .append(";BOX64_DYNAREC_SAFEFLAGS=0")
                  .append(";BOX64_DYNAREC_FASTNAN=1")
                  .append(";BOX64_DYNAREC_FASTROUND=1")
                  .append(";WINEDEBUG=-all");
        }
        return sb.toString();
    }

    /** JSON sesuai format .container Winlator. */
    public String toContainerJson(int id) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(id).append(",\n");
        sb.append("  \"name\": ").append(q(name)).append(",\n");
        sb.append("  \"screenSize\": ").append(q(screenSize)).append(",\n");
        sb.append("  \"graphicsDriver\": ").append(q(graphicsDriver)).append(",\n");
        sb.append("  \"dxwrapper\": ").append(q(dxWrapper)).append(",\n");
        sb.append("  \"dxwrapperConfig\": ").append(q(dxWrapperConfig)).append(",\n");
        sb.append("  \"audioDriver\": ").append(q(audioDriver)).append(",\n");
        sb.append("  \"box64Preset\": ").append(q(box64Preset)).append(",\n");
        sb.append("  \"wow64Mode\": ").append(wow64Mode).append(",\n");
        sb.append("  \"showFPS\": ").append(showFps).append(",\n");
        sb.append("  \"drives\": ").append(q(drives)).append(",\n");
        sb.append("  \"envVars\": ").append(q(envVars)).append(",\n");
        sb.append("  \"startupSelection\": \"ESSENTIAL\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** File teks pendamping yang dibaca setup-samp.sh (nick, target fps, dll). */
    public String toSetupArgs(String nickname, String server) {
        return "--nick \"" + nickname + "\""
                + " --server \"" + server + "\""
                + " --fps " + fpsTarget
                + " --driver " + graphicsDriver
                + " --res " + screenSize + "\n";
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    @Override
    public String toString() {
        return name + " " + screenSize + " " + graphicsDriver + "/" + dxWrapper
                + " box64=" + box64Preset + " wow64=" + wow64Mode + " fps=" + fpsTarget;
    }

    // ---------------------------------------------------------------- builder --

    public static final class Builder {
        private final String name;
        private String screenSize = "1280x720";
        private String graphicsDriver = "turnip";
        private String dxWrapper = "dxvk";
        private String dxWrapperConfig = "";
        private String audioDriver = "alsa";
        private String box64Preset = "performance";
        private boolean wow64Mode = true;
        private boolean showFps = false;
        private String drives = "";
        private String envVars = "";
        private int fpsTarget = 30;

        public Builder(String name) { this.name = name; }
        public Builder screenSize(String v) { screenSize = v; return this; }
        public Builder graphicsDriver(String v) { graphicsDriver = v; return this; }
        public Builder dxWrapper(String v) { dxWrapper = v; return this; }
        public Builder dxWrapperConfig(String v) { dxWrapperConfig = v; return this; }
        public Builder audioDriver(String v) { audioDriver = v; return this; }
        public Builder box64Preset(String v) { box64Preset = v; return this; }
        public Builder wow64Mode(boolean v) { wow64Mode = v; return this; }
        public Builder showFps(boolean v) { showFps = v; return this; }
        public Builder drives(String v) { drives = v; return this; }
        public Builder envVars(String v) { envVars = v; return this; }
        public Builder fpsTarget(int v) { fpsTarget = v; return this; }
        public ContainerPreset build() { return new ContainerPreset(this); }
    }
}
