package com.sampwrap.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import com.sampwrap.core.DeviceProfile;

/** Baca spek HP lalu pilih preset optimasi. */
public final class DeviceScan {

    private DeviceScan() {}

    public static DeviceProfile.Spec spec(Context ctx) {
        String soc = socModel();
        String gpu = glRenderer(ctx);
        int ramMb = ramMb(ctx);
        int cores = Runtime.getRuntime().availableProcessors();
        boolean vulkan = hasVulkan(ctx);
        boolean arm64 = isArm64();
        return new DeviceProfile.Spec(soc, gpu, ramMb, Build.VERSION.SDK_INT, vulkan, arm64, cores);
    }

    public static String socModel() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                String m = Build.SOC_MODEL;      // contoh: "Helio G36"
                String manu = Build.SOC_MANUFACTURER;
                if (m != null && !m.isEmpty() && !m.equals("unknown")) {
                    return (manu == null || manu.isEmpty() || manu.equals("unknown"))
                            ? m : manu + " " + m;
                }
            }
        } catch (Throwable ignored) { }
        String hw = Build.HARDWARE == null ? "" : Build.HARDWARE;
        return hw.isEmpty() ? Build.BOARD : hw;
    }

    public static int ramMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return (int) (mi.totalMem / (1024L * 1024L));
    }

    public static boolean hasVulkan(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isArm64() {
        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            if (abi.contains("arm64")) return true;
        }
        return false;
    }

    /**
     * Nama GPU sebenarnya (Adreno 740 / Mali-G57 / PowerVR GE8320).
     * Dibuat EGL context 1x1 lalu dibuang — aman dipanggil dari thread latar.
     */
    public static String glRenderer(Context ctx) {
        EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (d == EGL14.EGL_NO_DISPLAY) return guessFromHardware();
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(d, ver, 0, ver, 1)) return guessFromHardware();

        int[] attrib = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE};
        EGLConfig[] cfg = new EGLConfig[1];
        int[] n = new int[1];
        if (!EGL14.eglChooseConfig(d, attrib, 0, cfg, 0, 1, n, 0) || n[0] == 0) {
            EGL14.eglTerminate(d);
            return guessFromHardware();
        }
        int[] ctxAttr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        EGLContext c = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
        int[] surfAttr = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
        EGLSurface s = EGL14.eglCreatePbufferSurface(d, cfg[0], surfAttr, 0);
        String out = null;
        if (c != EGL14.EGL_NO_CONTEXT && s != EGL14.EGL_NO_SURFACE) {
            if (EGL14.eglMakeCurrent(d, s, s, c)) {
                out = GLES20.glGetString(GLES20.GL_RENDERER);
            }
            EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(d, s);
            EGL14.eglDestroyContext(d, c);
        }
        EGL14.eglTerminate(d);
        return (out == null || out.isEmpty()) ? guessFromHardware() : out;
    }

    /** Kalau EGL gagal, tebak dari nama hardware (cukup buat milih tier). */
    private static String guessFromHardware() {
        String hw = (Build.HARDWARE + " " + Build.BOARD + " " + socModel()).toLowerCase();
        if (hw.contains("qcom") || hw.contains("sm") || hw.contains("msm")) return "Adreno";
        if (hw.contains("mt") || hw.contains("exynos")) return "Mali";
        if (hw.contains("power")) return "PowerVR";
        return "unknown";
    }

    public static String describe(DeviceProfile.Spec s) {
        return s.soc + "\n"
                + s.gpu + "\n"
                + "RAM " + s.ramMb + " MB · " + s.cores + " core · Android API " + s.sdk + "\n"
                + (s.supportsVulkan ? "Vulkan tersedia" : "Vulkan TIDAK tersedia")
                + " · " + (s.is64BitHost ? "ARM64" : "ARM 32-bit");
    }
}
