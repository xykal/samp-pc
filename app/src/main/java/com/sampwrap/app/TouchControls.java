package com.sampwrap.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Overlay kontrol SA-MP (menggantikan Winlator InputControls).
 *
 * Kenapa ditulis sendiri: layout Winlator dibuat untuk desktop (drag-drive,
 * keyboard on-screen). SA-MP butuh: stick analog, area geser untuk kamera,
 * fire/aim pemisah, tombol chat, dan tombol cepat perintah server.
 *
 * Semua event keluar lewat {@link Sink}. Di app mandiri, sink default
 * mengirim KeyEvent/MotionEvent ke window aktif. Saat digabung dengan fork
 * Winlator, ganti sink-nya supaya event masuk ke XServer input pipeline
 * (lihat README bagian 5).
 *
 * Layout dibaca dari assets/control_layout.json → bisa diedit user
 * (drag untuk pindah, pinch/slider untuk ukuran) lalu disimpan ke prefs.
 */
public class TouchControls extends View {

    /** Tujuan event. */
    public interface Sink {
        void onKey(String key, boolean down);
        void onMouseMove(float dx, float dy);
        void onText(String text);
    }

    // ------------------------------------------------------------- model ----

    public static final class Btn {
        String id, label, key, hint, color;
        float x, y, size;
        boolean toggle, pressed;
    }

    public static final class Stick {
        float x, y, radius, deadzone;
        String up, down, left, right;
        float pointerId = -1;
        float dx, dy;
    }

    public static final class Look {
        boolean enabled;
        float x, y, w, h;
        float sensitivity = 1f;
        float lastX, lastY;
        int pointerId = -1;
    }

    private final List<Btn> buttons = new ArrayList<>();
    private Stick stick = new Stick();
    private Look look = new Look();
    private final List<String[]> quick = new ArrayList<>();

    private Sink sink;
    private boolean editMode;
    private float scale = 1f;
    private float density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchControls(Context c) { this(c, null); }

    public TouchControls(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.argb(150, 255, 255, 255));
        setFocusable(false);
        load(c);
    }

    public void setSink(Sink s) {
        this.sink = s;
    }

    public void setEditMode(boolean on) {
        editMode = on;
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setUiScale(float s) {
        scale = Math.max(0.6f, Math.min(1.6f, s));
        invalidate();
    }

    public float uiScale() {
        return scale;
    }

    // --------------------------------------------------------------- load ----

    public void load(Context c) {
        buttons.clear();
        quick.clear();
        try (InputStream in = c.getAssets().open("control_layout.json")) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            parse(new JSONObject(sb.toString()));
        } catch (Exception e) {
            // fallback minimal supaya game tetap bisa dimainkan
            Btn b = new Btn();
            b.id = "fire"; b.label = "FIRE"; b.key = "MOUSE_LEFT";
            b.x = 980; b.y = 320; b.size = 84;
            buttons.add(b);
        }
        invalidate();
    }

    private void parse(JSONObject o) throws Exception {
        JSONObject js = o.optJSONObject("stick");
        if (js != null) {
            stick.x = (float) js.optDouble("x", 92);
            stick.y = (float) js.optDouble("y", 300);
            stick.radius = (float) js.optDouble("radius", 78);
            stick.deadzone = (float) js.optDouble("deadzone", 0.12);
            JSONObject b = js.optJSONObject("bindings");
            if (b != null) {
                stick.up = b.optString("up", "W");
                stick.down = b.optString("down", "S");
                stick.left = b.optString("left", "A");
                stick.right = b.optString("right", "D");
            }
        }
        JSONObject jl = o.optJSONObject("look");
        if (jl != null) {
            look.enabled = jl.optBoolean("enabled", true);
            look.x = (float) jl.optDouble("x", 760);
            look.y = (float) jl.optDouble("y", 150);
            look.w = (float) jl.optDouble("w", 240);
            look.h = (float) jl.optDouble("h", 240);
            look.sensitivity = (float) jl.optDouble("sensitivity", 1.0);
        }
        JSONArray arr = o.optJSONArray("buttons");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Btn b = new Btn();
                b.id = j.optString("id", "btn" + i);
                b.label = j.optString("label", "?");
                b.key = j.optString("key", "");
                b.hint = j.optString("hint", "");
                b.color = j.optString("color", "default");
                b.x = (float) j.optDouble("x", 100);
                b.y = (float) j.optDouble("y", 100);
                b.size = (float) j.optDouble("size", 60);
                b.toggle = j.optBoolean("toggle", false);
                buttons.add(b);
            }
        }
        JSONArray qa = o.optJSONArray("quick");
        if (qa != null) {
            for (int i = 0; i < qa.length(); i++) {
                JSONObject j = qa.getJSONObject(i);
                quick.add(new String[]{j.optString("label"), j.optString("send")});
            }
        }
    }

    // --------------------------------------------------------------- draw ----

    private float dp(float v) {
        return v * density;
    }

    private RectF rectOf(Btn b) {
        float s = dp(b.size) * scale;
        float cx = dp(b.x) * scale;
        float cy = dp(b.y) * scale;
        return new RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);

        // area kamera
        if (look.enabled) {
            fill.setColor(Color.argb(editMode ? 40 : 16, 255, 255, 255));
            cv.drawRoundRect(new RectF(dp(look.x) * scale, dp(look.y) * scale,
                            (dp(look.x) + dp(look.w)) * scale, (dp(look.y) + dp(look.h)) * scale),
                    dp(16), dp(16), fill);
            if (editMode) {
                textPaint.setTextSize(dp(12));
                cv.drawText("AREA KAMERA", (dp(look.x) + dp(look.w) / 2) * scale,
                        (dp(look.y) + dp(look.h) / 2) * scale, textPaint);
            }
        }

        // stick
        float cx = dp(stick.x) * scale, cy = dp(stick.y) * scale;
        float r = dp(stick.radius) * scale;
        fill.setColor(Color.argb(70, 0, 0, 0));
        cv.drawCircle(cx, cy, r, fill);
        cv.drawCircle(cx, cy, r, stroke);
        cv.drawCircle(cx + stick.dx * r, cy + stick.dy * r, r * 0.42f,
                fillColor(Color.argb(160, 240, 160, 32)));

        // tombol
        textPaint.setTextSize(dp(13) * scale);
        for (Btn b : buttons) {
            RectF rf = rectOf(b);
            int base = colorOf(b.color);
            fill.setColor(b.pressed
                    ? Color.argb(220, Color.red(base), Color.green(base), Color.blue(base))
                    : Color.argb(110, Color.red(base), Color.green(base), Color.blue(base)));
            cv.drawRoundRect(rf, rf.width() / 3, rf.height() / 3, fill);
            cv.drawRoundRect(rf, rf.width() / 3, rf.height() / 3, stroke);
            cv.drawText(b.label, rf.centerX(), rf.centerY() + dp(5) * scale, textPaint);
        }

        // tombol cepat chat
        float qx = dp(20) * scale, qy = dp(20) * scale;
        textPaint.setTextSize(dp(11) * scale);
        for (String[] q : quick) {
            float w = dp(64) * scale, h = dp(26) * scale;
            RectF rf = new RectF(qx, qy, qx + w, qy + h);
            fill.setColor(Color.argb(90, 240, 160, 32));
            cv.drawRoundRect(rf, h / 2, h / 2, fill);
            cv.save();
            textPaint.setTextSize(dp(10) * scale);
            cv.drawText(q[0], rf.centerX(), rf.centerY() + dp(4) * scale, textPaint);
            cv.restore();
            qy += h + dp(6) * scale;
        }

        if (editMode) {
            textPaint.setTextSize(dp(14));
            textPaint.setColor(UiKit.ACCENT);
            cv.drawText("MODE EDIT — geser tombol, tekan SIMPAN", getWidth() / 2f,
                    getHeight() - dp(16), textPaint);
            textPaint.setColor(Color.WHITE);
        }
    }

    private Paint fillColor(int c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(c);
        return p;
    }

    private int colorOf(String name) {
        if ("danger".equals(name)) return UiKit.DANGER;
        if ("accent".equals(name)) return UiKit.ACCENT;
        return Color.rgb(90, 100, 115);
    }

    // -------------------------------------------------------------- touch ----

    private Btn hitButton(float x, float y) {
        for (Btn b : buttons) {
            if (rectOf(b).contains(x, y)) return b;
        }
        return null;
    }

    private Btn dragged;
    private float dragOffsetX, dragOffsetY;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float x = ev.getX(), y = ev.getY();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                float px = ev.getX(idx), py = ev.getY(idx);

                if (editMode) {
                    Btn b = hitButton(px, py);
                    if (b != null) {
                        dragged = b;
                        dragOffsetX = px - dp(b.x) * scale;
                        dragOffsetY = py - dp(b.y) * scale;
                    }
                    return true;
                }

                // stick?
                float sx = dp(stick.x) * scale, sy = dp(stick.y) * scale;
                float r = dp(stick.radius) * scale * 1.35f;
                if (stick.pointerId < 0
                        && Math.hypot(px - sx, py - sy) < r) {
                    stick.pointerId = ev.getPointerId(idx);
                    stick.dx = 0; stick.dy = 0;
                    return true;
                }

                // area kamera
                if (look.enabled && look.pointerId < 0) {
                    float lx = dp(look.x) * scale, ly = dp(look.y) * scale;
                    float lw = dp(look.w) * scale, lh = dp(look.h) * scale;
                    if (px >= lx && px <= lx + lw && py >= ly && py <= ly + lh) {
                        look.pointerId = ev.getPointerId(idx);
                        look.lastX = px;
                        look.lastY = py;
                        return true;
                    }
                }

                // tombol
                Btn b = hitButton(px, py);
                if (b != null) {
                    b.pressed = true;
                    emit(b.key, true);
                    if (b.toggle) b.pressed = !b.pressed;
                    vibrate();
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (editMode) {
                    if (dragged != null) {
                        dragged.x = (x - dragOffsetX) / scale / density;
                        dragged.y = (y - dragOffsetY) / scale / density;
                        invalidate();
                    }
                    return true;
                }

                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int pid = ev.getPointerId(i);
                    float px = ev.getX(i), py = ev.getY(i);

                    if (pid == stick.pointerId) {
                        float sx = dp(stick.x) * scale, sy = dp(stick.y) * scale;
                        float r = dp(stick.radius) * scale;
                        float ndx = (px - sx) / r;
                        float ndy = (py - sy) / r;
                        float len = (float) Math.hypot(ndx, ndy);
                        if (len > 1f) { ndx /= len; ndy /= len; }
                        setStick(ndx, ndy);
                        invalidate();
                    } else if (pid == look.pointerId) {
                        float dx = px - look.lastX;
                        float dy = py - look.lastY;
                        look.lastX = px;
                        look.lastY = py;
                        if (sink != null && (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f)) {
                            sink.onMouseMove(dx * look.sensitivity * 1.6f,
                                    dy * look.sensitivity * 1.6f);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);

                if (editMode) {
                    dragged = null;
                    invalidate();
                    return true;
                }

                if (pid == stick.pointerId) {
                    stick.pointerId = -1;
                    setStick(0, 0);
                    invalidate();
                    return true;
                }
                if (pid == look.pointerId) {
                    look.pointerId = -1;
                    return true;
                }

                float px = ev.getX(idx), py = ev.getY(idx);
                Btn b = hitButton(px, py);
                if (b != null && !b.toggle) {
                    b.pressed = false;
                    emit(b.key, false);
                    invalidate();
                }
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    private void setStick(float dx, float dy) {
        stick.dx = dx;
        stick.dy = dy;
        boolean up = dy < -stick.deadzone;
        boolean down = dy > stick.deadzone;
        boolean left = dx < -stick.deadzone;
        boolean right = dx > stick.deadzone;

        if (sink != null) {
            sink.onKey(stick.up, up);
            sink.onKey(stick.down, down);
            sink.onKey(stick.left, left);
            sink.onKey(stick.right, right);
        }
    }

    private void emit(String key, boolean down) {
        if (sink != null && key != null && !key.isEmpty()) sink.onKey(key, down);
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(12);
        } catch (Throwable ignored) { }
    }

    /** Simpan posisi/ukuran hasil mode edit ke prefs (dipakai saat start berikutnya). */
    public String exportJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", "custom");
            JSONObject js = new JSONObject();
            js.put("x", stick.x); js.put("y", stick.y);
            js.put("radius", stick.radius); js.put("deadzone", stick.deadzone);
            JSONObject b = new JSONObject();
            if (stick.up != null) b.put("up", stick.up);
            if (stick.down != null) b.put("down", stick.down);
            if (stick.left != null) b.put("left", stick.left);
            if (stick.right != null) b.put("right", stick.right);
            js.put("bindings", b);
            o.put("stick", js);

            JSONObject jl = new JSONObject();
            jl.put("enabled", look.enabled); jl.put("x", look.x); jl.put("y", look.y);
            jl.put("w", look.w); jl.put("h", look.h); jl.put("sensitivity", look.sensitivity);
            o.put("look", jl);

            JSONArray arr = new JSONArray();
            for (Btn b2 : buttons) {
                JSONObject j = new JSONObject();
                j.put("id", b2.id); j.put("label", b2.label); j.put("key", b2.key);
                j.put("x", b2.x); j.put("y", b2.y); j.put("size", b2.size);
                j.put("color", b2.color == null ? "default" : b2.color);
                j.put("toggle", b2.toggle);
                arr.put(j);
            }
            o.put("buttons", arr);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
