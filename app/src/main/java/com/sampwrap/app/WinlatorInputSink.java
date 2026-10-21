package com.sampwrap.app;

import android.view.KeyEvent;

import java.lang.reflect.Method;

/**
 * Jembatan TouchControls -> input pipeline Winlator.
 *
 * Dipakai SETELAH app ini digabung ke fork Winlator (lihat docs/ONE-APK.md).
 * Semua panggilan lewat refleksi supaya file ini tetap bisa dikompilasi
 * walaupun nama method di fork berbeda antar versi:
 *
 *   - com.winlator.inputcontrols.InputDevice#sendKeyEvent(KeyEvent)
 *   - com.winlator.inputcontrols.InputDevice#mouseMove(float, float)  (atau int,int)
 *   - com.winlator.inputcontrols.InputDevice#sendMouseButton(boolean down, int button)
 *
 * Kalau method tidak ada, sink jatuh ke mode "keyboard event ke window aktif"
 * (masih berguna untuk app mandiri / debug).
 */
public final class WinlatorInputSink implements TouchControls.Sink {

    private final Object inputDevice;
    private Method mSendKey, mMouseMove, mMouseButton;
    private boolean reflectOk;

    public WinlatorInputSink(Object winlatorInputDevice) {
        this.inputDevice = winlatorInputDevice;
        if (inputDevice == null) return;
        Class<?> c = inputDevice.getClass();

        mSendKey = find(c, "sendKeyEvent", KeyEvent.class);

        mMouseMove = find(c, "mouseMove", float.class, float.class);
        if (mMouseMove == null) mMouseMove = find(c, "mouseMove", int.class, int.class);

        mMouseButton = find(c, "sendMouseButton", boolean.class, int.class);
        if (mMouseButton == null) mMouseButton = find(c, "sendMouseButton", int.class, boolean.class);

        reflectOk = mSendKey != null || mMouseMove != null || mMouseButton != null;
    }

    private static Method find(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isBridged() {
        return reflectOk;
    }

    // ------------------------------------------------------------- Sink ------

    @Override
    public void onKey(String key, boolean down) {
        int code = keyCode(key);
        if (code < 0) {
            if ("MOUSE_LEFT".equals(key)) {
                mouseButton(0, down);
            } else if ("MOUSE_RIGHT".equals(key)) {
                mouseButton(1, down);
            }
            return;
        }
        KeyEvent ev = new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
        if (mSendKey != null) {
            try {
                mSendKey.invoke(inputDevice, ev);
                return;
            } catch (Throwable ignored) { }
        }
    }

    @Override
    public void onMouseMove(float dx, float dy) {
        if (mMouseMove == null) return;
        try {
            Class<?>[] types = mMouseMove.getParameterTypes();
            if (types[0] == float.class) {
                mMouseMove.invoke(inputDevice, dx, dy);
            } else {
                mMouseMove.invoke(inputDevice, Math.round(dx), Math.round(dy));
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onText(String text) {
        for (char ch : text.toCharArray()) {
            int code = charToKeyCode(ch);
            if (code >= 0) onKeyEvent(code);
        }
    }

    private void onKeyEvent(int code) {
        if (mSendKey == null) return;
        try {
            mSendKey.invoke(inputDevice, new KeyEvent(KeyEvent.ACTION_DOWN, code));
            mSendKey.invoke(inputDevice, new KeyEvent(KeyEvent.ACTION_UP, code));
        } catch (Throwable ignored) { }
    }

    private void mouseButton(int button, boolean down) {
        if (mMouseButton == null) return;
        try {
            Class<?>[] types = mMouseButton.getParameterTypes();
            if (types[0] == boolean.class) {
                mMouseButton.invoke(inputDevice, down, button);
            } else {
                mMouseButton.invoke(inputDevice, button, down);
            }
        } catch (Throwable ignored) { }
    }

    // --------------------------------------------------------- key mapping ---

    /** Nama tombol di control_layout.json -> KeyEvent Android. */
    public static int keyCode(String key) {
        if (key == null) return -1;
        switch (key) {
            case "W": return KeyEvent.KEYCODE_W;
            case "A": return KeyEvent.KEYCODE_A;
            case "S": return KeyEvent.KEYCODE_S;
            case "D": return KeyEvent.KEYCODE_D;
            case "F": return KeyEvent.KEYCODE_F;
            case "H": return KeyEvent.KEYCODE_H;
            case "T": return KeyEvent.KEYCODE_T;
            case "M": return KeyEvent.KEYCODE_M;
            case "G": return KeyEvent.KEYCODE_G;
            case "ENTER": return KeyEvent.KEYCODE_ENTER;
            case "ESC": return KeyEvent.KEYCODE_ESCAPE;
            case "TAB": return KeyEvent.KEYCODE_TAB;
            case "SPACE": return KeyEvent.KEYCODE_SPACE;
            case "SHIFT": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "CTRL": return KeyEvent.KEYCODE_CTRL_LEFT;
            default: return -1;
        }
    }

    private static int charToKeyCode(char c) {
        if (c >= 'a' && c <= 'z') c = Character.toUpperCase(c);
        if (c >= 'A' && c <= 'Z') return KeyEvent.KEYCODE_A + (c - 'A');
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        if (c == ' ') return KeyEvent.KEYCODE_SPACE;
        if (c == '/') return KeyEvent.KEYCODE_SLASH;
        return -1;
    }
}
