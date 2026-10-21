package com.sampwrap.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Helper UI minimalis (tanpa AndroidX, tanpa layout XML). */
public final class UiKit {

    public static final int BG = Color.rgb(20, 24, 31);
    public static final int CARD = Color.rgb(30, 36, 46);
    public static final int ACCENT = Color.rgb(240, 160, 32);
    public static final int TEXT = Color.WHITE;
    public static final int MUTED = Color.rgb(150, 158, 170);
    public static final int DANGER = Color.rgb(230, 90, 80);
    public static final int OK = Color.rgb(90, 200, 140);

    private UiKit() {}

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static TextView text(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, 14));
        l.setBackground(g);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return l;
    }

    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.rgb(20, 24, 31) : TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : CARD);
        g.setCornerRadius(dp(c, 12));
        b.setBackground(g);
        b.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return b;
    }

    /** Warna garis tipis untuk border kartu/dialog. */
    public static int line(Context c) {
        return Color.rgb(48, 56, 68);
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.rgb(48, 56, 68));
        return v;
    }

    public static TextView badge(Context c, String text, int color) {
        TextView t = text(c, text, 12, true, color);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        g.setCornerRadius(dp(c, 20));
        g.setStroke(1, color);
        t.setBackground(g);
        t.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 4));
        return t;
    }
}
