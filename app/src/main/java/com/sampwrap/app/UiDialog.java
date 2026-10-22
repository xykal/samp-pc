package com.sampwrap.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Dialog + progress bergaya app sendiri.
 *
 * Kenapa bukan AlertDialog/ProgressDialog: keduanya ikut style sistem
 * (Holo/Material bawaan Android), jadi kelihatan "app template". Di sini
 * semuanya digambar sendiri: kartu gelap membulat, tombol pill, progress bar
 * kotak yang kita gambar manual.
 */
public final class UiDialog {

    private UiDialog() {}

    // ------------------------------------------------------------------ Alert --

    public static final class Alert {
        private final Context ctx;
        private String title, message, posText, negText, neuText;
        private Runnable onNeu;
        private Runnable onPos, onNeg;
        private boolean cancelable = true;
        private String preText;
        private CharSequence[] items;
        private int checkedIndex = -1;
        private DialogInterface.OnClickListener itemListener;

        public Alert(Context c) { this.ctx = c; }

        public Alert title(String t) { this.title = t; return this; }
        public Alert message(String m) { this.message = m; return this; }

        /** Teks panjang (log/laporan): digulir, huruf monospace, bisa diseleksi. */
        public Alert preformatted(String t) { this.preText = t; return this; }
        public Alert positive(String t, Runnable r) { this.posText = t; this.onPos = r; return this; }
        public Alert negative(String t, Runnable r) { this.negText = t; this.onNeg = r; return this; }
        public Alert cancelable(boolean b) { this.cancelable = b; return this; }

        /** Daftar pilihan (pengganti AlertDialog.setItems). */
        public Alert setItems(CharSequence[] it, DialogInterface.OnClickListener l) {
            this.items = it; this.itemListener = l; return this;
        }

        /** Daftar pilihan dengan tanda terpilih (pengganti setSingleChoiceItems). */
        public Alert setSingleChoiceItems(CharSequence[] it, int checked, DialogInterface.OnClickListener l) {
            this.items = it; this.checkedIndex = checked; this.itemListener = l; return this;
        }

        // ---- API kompatibel AlertDialog.Builder (biar gampang dipakai) ----
        public Alert setTitle(CharSequence t) { return title(String.valueOf(t)); }
        public Alert setMessage(CharSequence m) { return message(String.valueOf(m)); }
        public Alert setCancelable(boolean b) { return cancelable(b); }
        public Alert setPositiveButton(CharSequence t, final DialogInterface.OnClickListener l) {
            this.posText = String.valueOf(t);
            this.onPos = l == null ? null : () -> l.onClick(null, DialogInterface.BUTTON_POSITIVE);
            return this;
        }
        public Alert setNegativeButton(CharSequence t, final DialogInterface.OnClickListener l) {
            this.negText = String.valueOf(t);
            this.onNeg = l == null ? null : () -> l.onClick(null, DialogInterface.BUTTON_NEGATIVE);
            return this;
        }
        public Alert setNeutralButton(CharSequence t, final DialogInterface.OnClickListener l) {
            this.neuText = String.valueOf(t);
            this.onNeu = l == null ? null : () -> l.onClick(null, DialogInterface.BUTTON_NEUTRAL);
            return this;
        }

        public Alert neutral(String t, Runnable r) { this.neuText = t; this.onNeu = r; return this; }

        public void show() {
            final Dialog d = base(ctx);
            LinearLayout card = card(ctx, d);

            if (title != null) card.addView(UiKit.text(ctx, title, 18, true, UiKit.TEXT));
            if (message != null) {
                TextView tv = UiKit.text(ctx, message, 14, false, UiKit.MUTED);
                tv.setPadding(0, UiKit.dp(ctx, 8), 0, 0);
                card.addView(tv);
            }

            if (preText != null) {
                android.widget.ScrollView sc = new android.widget.ScrollView(ctx);
                TextView tv = new TextView(ctx);
                tv.setText(preText);
                tv.setTextSize(11f);
                tv.setTextColor(UiKit.TEXT);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setTextIsSelectable(true);
                int pad = UiKit.dp(ctx, 10);
                tv.setPadding(pad, pad, pad, pad);
                sc.addView(tv);
                int h = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.55f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, h);
                lp.topMargin = UiKit.dp(ctx, 8);
                card.addView(sc, lp);
            }

            if (items != null) {
                for (int i = 0; i < items.length; i++) {
                    final int idx = i;
                    boolean sel = (i == checkedIndex);
                    LinearLayout rowItem = new LinearLayout(ctx);
                    rowItem.setOrientation(LinearLayout.HORIZONTAL);
                    rowItem.setGravity(Gravity.CENTER_VERTICAL);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(sel ? UiKit.ACCENT : UiKit.BG);
                    bg.setCornerRadius(UiKit.dp(ctx, 12));
                    rowItem.setBackground(bg);
                    int pp = UiKit.dp(ctx, 14);
                    rowItem.setPadding(pp, pp, pp, pp);

                    TextView label = UiKit.text(ctx, String.valueOf(items[i]), 15, sel,
                            sel ? Color.BLACK : UiKit.TEXT);
                    rowItem.addView(label);
                    rowItem.setClickable(true);
                    rowItem.setOnClickListener(v -> {
                        d.dismiss();
                        if (itemListener != null) itemListener.onClick(null, idx);
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = UiKit.dp(ctx, 8);
                    card.addView(rowItem, lp);
                }
            }

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            row.setPadding(0, UiKit.dp(ctx, 18), 0, 0);

            if (neuText != null) {
                TextView b = pill(ctx, neuText, UiKit.CARD, UiKit.TEXT);
                b.setOnClickListener(v -> { d.dismiss(); if (onNeu != null) onNeu.run(); });
                row.addView(b);
            }
            if (negText != null) {
                TextView b = pill(ctx, negText, UiKit.CARD, UiKit.TEXT);
                b.setOnClickListener(v -> { d.dismiss(); if (onNeg != null) onNeg.run(); });
                row.addView(b);
            }
            if (posText != null) {
                TextView b = pill(ctx, posText, UiKit.ACCENT, Color.BLACK);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = UiKit.dp(ctx, 10);
                b.setOnClickListener(v -> { d.dismiss(); if (onPos != null) onPos.run(); });
                row.addView(b, lp);
            }
            if (row.getChildCount() > 0) card.addView(row);

            d.setCancelable(cancelable);
            d.setCanceledOnTouchOutside(cancelable);
            d.show();
        }
    }

    // --------------------------------------------------------------- Progress --

    /**
     * Progress dialog: judul, status, bar, persen, dan tombol batal.
     * Semua update dari thread mana pun (di-marshal ke main thread).
     */
    /** Callback teks untuk dialog input. */
    public interface OnText { void on(String value); }

    /** Dialog input sederhana (pengganti AlertDialog + EditText bawaan). */
    public static void input(Context ctx, String title, String hint,
                             String initial, final OnText cb) {
        final Dialog d = base(ctx);
        LinearLayout card = card(ctx, d);

        card.addView(UiKit.text(ctx, title, 17, true, UiKit.TEXT));
        if (hint != null && !hint.isEmpty()) {
            TextView h = UiKit.text(ctx, hint, 12, false, UiKit.MUTED);
            h.setPadding(0, UiKit.dp(ctx, 8), 0, 0);
            card.addView(h);
        }

        final android.widget.EditText et = new android.widget.EditText(ctx);
        et.setText(initial == null ? "" : initial);
        et.setTextColor(UiKit.TEXT);
        et.setHintTextColor(UiKit.MUTED);
        et.setTextSize(13);
        et.setSingleLine(false);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiKit.BG);
        bg.setCornerRadius(UiKit.dp(ctx, 12));
        et.setBackground(bg);
        int ep = UiKit.dp(ctx, 12);
        et.setPadding(ep, ep, ep, ep);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = UiKit.dp(ctx, 10);
        card.addView(et, elp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, UiKit.dp(ctx, 16), 0, 0);

        TextView cancel = pill(ctx, "Batal", UiKit.BG, UiKit.MUTED);
        cancel.setOnClickListener(v -> d.dismiss());
        row.addView(cancel);

        TextView ok = pill(ctx, "Simpan", UiKit.ACCENT, Color.BLACK);
        ok.setOnClickListener(v -> {
            d.dismiss();
            if (cb != null) cb.on(et.getText() == null ? "" : et.getText().toString());
        });
        row.addView(ok);

        card.addView(row);
        d.show();
        et.requestFocus();
    }

    public static final class Progress {
        private final Dialog dialog;
        private final TextView status, percent;
        private final Bar bar;
        private final Handler ui = new Handler(Looper.getMainLooper());
        private Runnable onCancel;

        Progress(Context ctx, String title, String initial) {
            dialog = base(ctx);
            LinearLayout card = card(ctx, dialog);

            card.addView(UiKit.text(ctx, title, 17, true, UiKit.TEXT));

            status = UiKit.text(ctx, initial == null ? "Menyiapkan…" : initial, 13, false, UiKit.MUTED);
            status.setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 10));
            card.addView(status);

            bar = new Bar(ctx);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 10));
            card.addView(bar, blp);

            percent = UiKit.text(ctx, "", 12, true, UiKit.ACCENT);
            percent.setPadding(0, UiKit.dp(ctx, 8), 0, 0);
            card.addView(percent);

            TextView cancel = pill(ctx, "Sembunyikan", UiKit.CARD, UiKit.MUTED);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = UiKit.dp(ctx, 16);
            cancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (onCancel != null) onCancel.run();
            });
            card.addView(cancel, clp);

            dialog.setCancelable(false);
        }

        public Progress onCancel(Runnable r) { this.onCancel = r; return this; }

        public void show() {
            if (!dialog.isShowing()) dialog.show();
        }

        public void dismiss() {
            ui.post(() -> { if (dialog.isShowing()) dialog.dismiss(); });
        }

        /** @param pct 0..100, atau -1 kalau tidak diketahui (indeterminate) */
        public void update(final String text, final int pct) {
            ui.post(() -> {
                if (text != null) status.setText(text);
                bar.setProgress(pct);
                if (pct >= 0) {
                    percent.setText(pct + "%");
                } else {
                    percent.setText("");
                }
            });
        }
    }

    public static Progress progress(Context ctx, String title, String initial) {
        return new Progress(ctx, title, initial);
    }

    // ------------------------------------------------------------ custom bar --

    /** Progress bar gambar sendiri: track bulat + isi gradasi. */
    public static final class Bar extends View {
        private final GradientDrawable track = new GradientDrawable();
        private final GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiKit.ACCENT, Color.rgb(255, 205, 90)});
        private float fraction = -1f;

        Bar(Context c) {
            super(c);
            track.setColor(UiKit.CARD);
            track.setCornerRadius(UiKit.dp(c, 6));
            fill.setCornerRadius(UiKit.dp(c, 6));
            setBackground(track);
        }

        private boolean animating;
        private float offset;

        void setProgress(int pct) {
            fraction = pct < 0 ? -1f : Math.min(100, pct) / 100f;
            if (fraction < 0 && !animating) startAnim();
            if (fraction >= 0 && animating) { animating = false; removeCallbacks(tick); }
            invalidate();
        }

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!animating) return;
                offset += UiKit.dp(getContext(), 3);
                if (offset > getWidth()) offset = -getWidth() * 0.35f;
                invalidate();
                postDelayed(this, 16);
            }
        };

        private void startAnim() {
            if (animating) return;
            animating = true;
            offset = -getWidth() * 0.35f;
            post(tick);
        }

        @Override protected void onDetachedFromWindow() {
            animating = false;
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(android.graphics.Canvas cv) {
            super.onDraw(cv);
            int h = getHeight();
            if (fraction > 0) {
                int w = (int) (getWidth() * fraction);
                if (w < UiKit.dp(getContext(), 10)) w = UiKit.dp(getContext(), 10);
                fill.setBounds(0, 0, w, h);
                fill.draw(cv);
                return;
            }
            // tidak diketahui totalnya -> segmen berjalan, biar jelas app masih kerja
            int seg = (int) (getWidth() * 0.3f);
            int x = (int) offset;
            fill.setBounds(x, 0, x + seg, h);
            fill.draw(cv);
        }
    }

    // ------------------------------------------------------------- primitives --

    private static Dialog base(Context ctx) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.7f);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return d;
    }

    private static LinearLayout card(Context ctx, Dialog d) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = UiKit.dp(ctx, 22);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiKit.CARD);
        bg.setCornerRadius(UiKit.dp(ctx, 20));
        bg.setStroke(UiKit.dp(ctx, 1), UiKit.line(ctx));
        card.setBackground(bg);

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setPadding(UiKit.dp(ctx, 20), 0, UiKit.dp(ctx, 20), 0);
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(ctx);
        sc.addView(wrap);

        d.setContentView(sc, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    static TextView pill(Context ctx, String text, int bgColor, int textColor) {
        TextView b = UiKit.text(ctx, text, 14, true, textColor);
        b.setGravity(Gravity.CENTER);
        int px = UiKit.dp(ctx, 20), py = UiKit.dp(ctx, 11);
        b.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(UiKit.dp(ctx, 24));
        b.setBackground(bg);
        b.setClickable(true);
        return b;
    }
}
