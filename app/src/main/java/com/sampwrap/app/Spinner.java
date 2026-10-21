package com.sampwrap.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Pengganti ProgressDialog bawaan (yang tampilannya ikut style sistem).
 * Dipakai untuk operasi singkat: impor ZIP, pasang mod, scan perangkat.
 */
public final class Spinner {

    private final UiDialog.Progress p;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean showing;

    public Spinner(Context c, String message) {
        this.p = UiDialog.progress(c, "SAMP PC", message);
    }

    public Spinner setMessage(String m) {
        p.update(m, -1);
        return this;
    }

    public Spinner setCancelable(boolean b) { return this; }

    public void show() {
        showing = true;
        p.show();
    }

    public void dismiss() {
        showing = false;
        p.dismiss();
    }

    public boolean isShowing() { return showing; }
}
