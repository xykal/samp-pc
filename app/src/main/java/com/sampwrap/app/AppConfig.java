package com.sampwrap.app;

import android.content.Context;

import java.io.InputStream;
import java.util.Properties;

/**
 * Konfigurasi app yang dibaca dari assets/app_config.properties.
 *
 * Kenapa bukan BuildConfig: app ini harus bisa dikompilasi DUA cara —
 *   1. standalone (repo ini)
 *   2. digabung ke fork Winlator (namespace & build script orang lain)
 * Dengan file assets, tidak ada satu pun perubahan build script yang dibutuhkan.
 *
 * Ubah branding/hosting cukup di assets/app_config.properties.
 */
public final class AppConfig {

    private static final Properties P = new Properties();
    private static boolean loaded;

    private AppConfig() {}

    public static synchronized void load(Context ctx) {
        if (loaded) return;
        try (InputStream in = ctx.getAssets().open("app_config.properties")) {
            P.load(in);
        } catch (Exception ignored) {
            // pakai default di bawah
        }
        loaded = true;
    }

    private static String get(String key, String def) {
        String v = P.getProperty(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public static String appLabel() {
        return get("app.label", "XversSamp");
    }

    public static String defaultServer() {
        return get("server.default", "127.0.0.1:7777");
    }

    /** Base URL untuk mode per-file. Kosong = mode arsip. */
    public static String packBaseUrl() {
        return get("pack.baseUrl", "");
    }

    /** URL satu ZIP paket. Kosong = mode per-file. */
    public static String packArchiveUrl() {
        return get("pack.archiveUrl", "");
    }

    /**
     * Paket client SA-MP (hasil bedah installer: samp.exe, samp.dll, SAMP/).
     * Kosong = lewati. Di-host terpisah karena kecil (±24 MB) dan bebas dibagi.
     */
    public static String packClientUrl() {
        return get("pack.clientUrl", "");
    }

    /**
     * Semua sumber paket, berurutan: yang utama dulu, lalu cermin.
     * Paket GTA SA di Drive sering diblokir kuota ("too many users…"),
     * jadi app harus punya jalan kedua tanpa user mengubah apa pun.
     */
    public static java.util.List<String> packArchiveSources() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        addUrl(out, packArchiveUrl());
        for (String s : get("pack.mirrors", "").split("[,\\n]")) addUrl(out, s.trim());
        return out;
    }

    /** Sumber client SA-MP (kecil, ±24 MB) — cerminnya opsional. */
    public static java.util.List<String> packClientSources() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        addUrl(out, packClientUrl());
        for (String s : get("pack.clientMirrors", "").split("[,\\n]")) addUrl(out, s.trim());
        return out;
    }

    private static void addUrl(java.util.List<String> out, String u) {
        if (u == null) return;
        u = u.trim();
        if (u.isEmpty() || out.contains(u)) return;
        out.add(u);
    }

    public static String manifestUrl() {
        String base = packBaseUrl();
        if (base.isEmpty()) return "";
        return base.endsWith("/") ? base + "manifest.tsv" : base + "/manifest.tsv";
    }
}
