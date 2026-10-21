package com.sampwrap.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Simpan pilihan user: paket data, path game, nickname, server. */
public final class Prefs {
    private static final String NAME = "sampwrap";
    private static final String K_PACK = "pack";
    private static final String K_NICK = "nick";
    private static final String K_SERVER = "server";
    private static final String K_MODS_DONE = "mods_done";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String pack(Context c) {
        return sp(c).getString(K_PACK, "lite");
    }

    public static void setPack(Context c, String v) {
        sp(c).edit().putString(K_PACK, v).apply();
    }

    public static String nick(Context c) {
        return sp(c).getString(K_NICK, "WargaBaru");
    }

    public static void setNick(Context c, String v) {
        sp(c).edit().putString(K_NICK, v).apply();
    }

    public static String server(Context c) {
        return sp(c).getString(K_SERVER, AppConfig.defaultServer());
    }

    public static void setServer(Context c, String v) {
        sp(c).edit().putString(K_SERVER, v).apply();
    }

    public static int modsInstalled(Context c) {
        return sp(c).getInt(K_MODS_DONE, 0);
    }

    public static void bumpMods(Context c) {
        sp(c).edit().putInt(K_MODS_DONE, modsInstalled(c) + 1).apply();
    }
}
