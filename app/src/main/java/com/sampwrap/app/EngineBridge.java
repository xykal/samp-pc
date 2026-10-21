package com.sampwrap.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.sampwrap.core.ContainerPreset;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Jembatan ke engine Winlator yang ikut dibungkus di APK yang SAMA.
 *
 * Semua akses pakai reflection supaya app ini tetap bisa di-build sendirian
 * (tanpa engine) — kalau class Winlator tidak ada, method play() balik ke
 * mode "engine terpisah".
 *
 * Dipakai karena APK gabungan pakai namespace com.winlator.cmod, jadi
 * getLaunchIntentForPackage("com.winlator.cmod") TIDAK menemukan apa pun
 * (paketnya ya app kita sendiri).
 */
final class EngineBridge {

    private static final String PKG          = "com.winlator.cmod";
    private static final String CLS_XSERVER  = PKG + ".XServerDisplayActivity";
    private static final String CLS_MAIN     = PKG + ".MainActivity";
    private static final String CLS_CM       = PKG + ".container.ContainerManager";
    private static final String CLS_CONTENTS = PKG + ".contents.ContentsManager";

    private EngineBridge() {}

    /** Ada engine Winlator di APK ini? */
    static boolean enginePresent() {
        try {
            Class.forName(CLS_XSERVER, false, EngineBridge.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Jalankan SA-MP langsung ke engine (UI emulator tidak muncul).
     *
     * @return null kalau berhasil start, atau pesan error buat ditampilkan.
     */
    static String play(Activity a, File gameDir, ContainerPreset preset) {
        File exe = new File(gameDir, "samp.exe");
        if (!exe.isFile()) {
            return "Data game belum terpasang.\n\n" + gameDir.getAbsolutePath()
                    + "\n\nTekan \"Pilih & unduh paket\" dulu (sekali saja), "
                    + "tunggu sampai muncul dialog \"Paket siap\".";
        }

        try {
            ClassLoader cl = a.getClassLoader();

            // ---- 1. ContainerManager -------------------------------------
            Class<?> cmCls = Class.forName(CLS_CM, true, cl);
            Constructor<?> cmCtor = cmCls.getConstructor(Context.class);
            Object cm = cmCtor.newInstance(a);

            Object container = firstContainer(cmCls, cm);
            if (container == null) container = createContainer(a, cl, cmCls, cm, preset);

            if (container == null) {
                // engine belum siap (imagefs belum diekstrak) -> buka setup
                return openEngineUi(a);
            }

            int id = ((Number) container.getClass().getMethod("getId")
                    .invoke(container)).intValue();

            // ---- 2. drive D: -> folder game ------------------------------
            String drives = String.valueOf(container.getClass().getMethod("getDrives")
                    .invoke(container));
            String want = "D:" + gameDir.getAbsolutePath();
            if (!drives.contains(want)) {
                Method setDrives = container.getClass().getMethod("setDrives", String.class);
                setDrives.invoke(container, want + drives);
                container.getClass().getMethod("saveData").invoke(container);
            }

            // ---- 3. shortcut .desktop -----------------------------------
            File deskDir = (File) container.getClass().getMethod("getDesktopDir")
                    .invoke(container);
            if (!deskDir.exists() && !deskDir.mkdirs()) {
                return "Tidak bisa menulis ke " + deskDir.getAbsolutePath();
            }

            File desktop = new File(deskDir, "SA-MP.desktop");
            writeDesktop(desktop, id, "\"D:\\\\samp.exe\"", AppConfig.appLabel());

            // ---- 4. langsung ke layar game ------------------------------
            Intent i = new Intent();
            i.setClassName(a, CLS_XSERVER);
            i.putExtra("container_id", id);
            i.putExtra("shortcut_path", desktop.getAbsolutePath());
            i.putExtra("shortcut_name", AppConfig.appLabel());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return null;

        } catch (Throwable t) {
            Log.e("EngineBridge", "gagal start engine", t);
            String err = openEngineUi(a);
            return err != null ? err : null;
        }
    }

    // ------------------------------------------------------------- helpers ----

    private static Object firstContainer(Class<?> cmCls, Object cm) {
        try {
            Object list = cmCls.getMethod("getContainers").invoke(cm);
            if (list instanceof List && !((List<?>) list).isEmpty()) {
                return ((List<?>) list).get(0);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * ContainerManager.createContainer(JSONObject, ContentsManager) itu private.
     * Kita panggil lewat reflection; dia yang mengekstrak pola prefix wine.
     */
    private static Object createContainer(Activity a, ClassLoader cl, Class<?> cmCls,
                                          Object cm, ContainerPreset preset) {
        try {
            JSONObject json = new JSONObject(preset.toContainerJson(1));
            Class<?> contentsCls = Class.forName(CLS_CONTENTS, true, cl);
            Object contents = contentsCls.getConstructor(Context.class).newInstance(a);

            Method m = cmCls.getDeclaredMethod("createContainer", JSONObject.class, contentsCls);
            m.setAccessible(true);
            return m.invoke(cm, json, contents);
        } catch (Throwable t) {
            Log.w("EngineBridge", "createContainer gagal: " + t);
            return null;
        }
    }

    private static void writeDesktop(File f, int containerId, String execWindows, String name)
            throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            w.write("[Desktop Entry]\n");
            w.write("Name=" + name + "\n");
            w.write("Exec=wine " + execWindows + "\n");
            w.write("Type=Application\n");
            w.write("container_id:" + containerId + "\n");
        }
    }

    /** Buka UI Winlator (setup pertama / darurat). */
    private static String openEngineUi(Activity a) {
        try {
            Intent i = new Intent();
            i.setClassName(a, CLS_MAIN);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            Toast.makeText(a, "Setup engine sekali di layar berikutnya",
                    Toast.LENGTH_LONG).show();
            return null;
        } catch (Throwable t) {
            try {
                Intent i = new Intent();
                i.setClassName(a, CLS_XSERVER);
                a.startActivity(i);
                return null;
            } catch (Throwable t2) {
                return "Engine tidak bisa dibuka: " + t2;
            }
        }
    }
}
