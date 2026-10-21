package com.sampwrap.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Layar atur kontrol: preview layout, ubah ukuran, geser tombol, simpan.
 * Hasilnya ditulis ke filesDir/control_layout.json dan dipakai TouchControls.
 */
public class ControlsActivity extends Activity {

    private TouchControls controls;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(10, 12, 16));

        controls = new TouchControls(this);
        controls.setSink(new TouchControls.Sink() {
            @Override public void onKey(String key, boolean down) {
                // Preview: kirim ke diri sendiri. Saat digabung dengan fork
                // Winlator, ganti dengan injeksi ke XServer (lihat README).
                if (down && "ESC".equals(key)) finish();
            }
            @Override public void onMouseMove(float dx, float dy) { }
            @Override public void onText(String text) { }
        });

        // muat layout tersimpan kalau ada
        File saved = new File(getFilesDir(), "control_layout.json");
        if (saved.exists()) loadSaved(saved);

        controls.setEditMode(true);
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // panel bawah
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(android.graphics.Color.argb(220, 20, 24, 31));
        int p = UiKit.dp(this, 12);
        panel.setPadding(p, p, p, p);

        TextView info = UiKit.text(this, "Geser tombol untuk pindah posisi", 12, false, UiKit.MUTED);
        panel.addView(info);

        TextView label = UiKit.text(this, "Ukuran: 100%", 13, true, UiKit.TEXT);
        panel.addView(label);

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(40); // 0.6 .. 1.6 -> 40 = 1.0
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                float scale = 0.6f + (v / 100f);
                controls.setUiScale(scale);
                label.setText(String.format("Ukuran: %.0f%%", scale * 100));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        panel.addView(sb);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ViewGroup.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        android.widget.Button save = UiKit.button(this, "SIMPAN", true);
        save.setOnClickListener(v -> {
            try {
                String json = controls.exportJson();
                File f = new File(getFilesDir(), "control_layout.json");
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                // simpan skala
                getSharedPreferences("sampwrap", MODE_PRIVATE).edit()
                        .putFloat("ui_scale", controls.uiScale()).apply();
                toast("Layout disimpan");
                finish();
            } catch (Exception e) {
                toast("Gagal: " + e.getMessage());
            }
        });
        row.addView(save, lp);

        android.widget.Button reset = UiKit.button(this, "RESET", false);
        reset.setOnClickListener(v -> {
            new File(getFilesDir(), "control_layout.json").delete();
            controls.load(ControlsActivity.this);
            controls.setEditMode(true);
            toast("Kembali ke layout default");
        });
        row.addView(reset, lp);

        panel.addView(row);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = android.view.Gravity.BOTTOM;
        root.addView(panel, plp);

        setContentView(root);
    }

    private void loadSaved(File f) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            in.close();
            android.content.res.AssetManager am = getAssets();
            // reuse parser lewat refleksi ringan: simpan ke assets tidak mungkin,
            // jadi kita pakai jalur langsung
            java.lang.reflect.Method m = TouchControls.class
                    .getDeclaredMethod("parse", org.json.JSONObject.class);
            m.setAccessible(true);
            m.invoke(controls, new org.json.JSONObject(sb.toString()));
            controls.invalidate();
        } catch (Throwable ignored) { }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
