package com.sampwrap.app;

import android.content.Context;

import com.sampwrap.core.Manifest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Mengambil manifest paket data:
 *   1. dari cache lokal (hasil unduh manifest terbaru), kalau ada
 *   2. kalau tidak, dari assets/manifest_default.tsv
 *
 * Kalau BuildConfig.PACK_BASE_URL diisi, manifest diunduh dari
 * <base>/manifest.tsv saat app dibuka (lihat MainActivity.refreshManifest).
 */
public final class ManifestStore {

    private static final String CACHE = "manifest.tsv";

    private ManifestStore() {}

    public static Manifest load(Context ctx) throws IOException {
        File cached = new File(ctx.getFilesDir(), CACHE);
        if (cached.exists() && cached.length() > 0) {
            return Manifest.load(cached.getAbsolutePath());
        }
        return fromAssets(ctx);
    }

    public static Manifest fromAssets(Context ctx) throws IOException {
        File out = new File(ctx.getFilesDir(), "manifest_assets.tsv");
        try (InputStream in = ctx.getAssets().open("manifest_default.tsv");
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return Manifest.load(out.getAbsolutePath());
    }

    public static void putCache(Context ctx, String tsv) throws IOException {
        File f = new File(ctx.getFilesDir(), CACHE);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(tsv.getBytes("UTF-8"));
        }
    }

    public static String manifestUrl() {
        String base = BuildConfig.PACK_BASE_URL;
        if (base == null || base.isEmpty()) return "";
        return base.endsWith("/") ? base + "manifest.tsv" : base + "/manifest.tsv";
    }
}
