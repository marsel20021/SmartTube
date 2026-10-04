package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.content.SharedPreferences;

public class PrefsUtil {
    public static String rawStringValue(SharedPreferences prefs, String key, String def) {
        try {
            Object v = prefs.getAll().get(key);
            if (v == null) return def;
            return String.valueOf(v);
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean rawBooleanValue(SharedPreferences prefs, String key, boolean def) {
        try {
            Object v = prefs.getAll().get(key);
            if (v == null) return def;
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return Boolean.parseBoolean((String) v);
            if (v instanceof Integer) return ((Integer) v) != 0;
            return def;
        } catch (Exception e) {
            return def;
        }
    }
}