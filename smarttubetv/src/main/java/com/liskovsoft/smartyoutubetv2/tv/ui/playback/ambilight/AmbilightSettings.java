package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.content.SharedPreferences;

/**
 * Снимок настроек подсветки. Читается из SharedPreferences целиком и не меняется после создания.
 * Значения по умолчанию ДОЛЖНЫ совпадать с android:defaultValue в res/xml/wled_settings.xml.
 */
final class AmbilightSettings {
    // --- Значения по умолчанию (единое место) ---
    private static final String DEF_IP = "192.168.1.185";
    private static final int DEF_PORT = 4048;
    private static final int DEF_FPS = 30;
    private static final int DEF_MARGIN = 2;        // %
    private static final int DEF_DEPTH = 5;         // %
    private static final int DEF_LEDS_TOP = 54;
    private static final int DEF_LEDS_BOTTOM = 54;
    private static final int DEF_LEDS_LEFT = 32;
    private static final int DEF_LEDS_RIGHT = 32;
    private static final int DEF_BRIGHTNESS = 100;  // %
    private static final int DEF_GAMMA = 22;        // 22 = 2.2
    private static final int DEF_SMOOTHING = 50;    // %
    private static final int DEF_BLACK_THRESHOLD = 15;
    private static final int DEF_SATURATION = 115;  // %, 100 = без изменений

    final boolean enabled;
    final String ip;
    final int port;
    final int fps;

    final float marginX;     // доля 0..1
    final float marginY;
    final float depth;       // доля 0..1
    final int ledsTop, ledsBottom, ledsLeft, ledsRight;
    final int direction;     // 0 = по часовой, 1 = против
    final int offset;

    final float brightness;  // 0..1
    final double gamma;
    final float smoothing;   // коэффициент на кадр при 30 fps (0.03..1)
    final float saturation;  // 1 = без изменений
    final boolean blackout;  // гасить тёмные зоны
    final int blackThreshold;
    final boolean letterboxDetect;
    final AmbilightController.CaptureMode captureMode;

    private AmbilightSettings(SharedPreferences p) {
        enabled = PrefsUtil.rawBooleanValue(p, "ambilight_enabled", true);
        ip = PrefsUtil.rawStringValue(p, "wled_ip", DEF_IP).trim();
        port = getInt(p, "udp_port", DEF_PORT, 1, 65535);
        fps = getInt(p, "fps", DEF_FPS, 1, 60);

        marginX = getInt(p, "margin_x", DEF_MARGIN, 0, 40) / 100f;
        marginY = getInt(p, "margin_y", DEF_MARGIN, 0, 40) / 100f;
        depth = getInt(p, "capture_depth", DEF_DEPTH, 1, 50) / 100f;

        ledsTop = getInt(p, "leds_top", DEF_LEDS_TOP, 0, 1000);
        ledsBottom = getInt(p, "leds_bottom", DEF_LEDS_BOTTOM, 0, 1000);
        ledsLeft = getInt(p, "leds_left", DEF_LEDS_LEFT, 0, 1000);
        ledsRight = getInt(p, "leds_right", DEF_LEDS_RIGHT, 0, 1000);
        direction = getInt(p, "direction", 0, 0, 1);
        offset = getInt(p, "led_offset", 0, 0, 5000);

        brightness = getInt(p, "brightness", DEF_BRIGHTNESS, 0, 100) / 100f;
        gamma = getInt(p, "gamma", DEF_GAMMA, 5, 50) / 10.0;
        float smoothingPercent = getInt(p, "smoothing", DEF_SMOOTHING, 0, 100) / 100f;
        smoothing = Math.max(0.03f, Math.min(1f, 1f - smoothingPercent));
        saturation = getInt(p, "saturation", DEF_SATURATION, 0, 300) / 100f;
        blackout = PrefsUtil.rawBooleanValue(p, "use_v2", true);
        blackThreshold = getInt(p, "black_threshold", DEF_BLACK_THRESHOLD, 0, 255);
        letterboxDetect = PrefsUtil.rawBooleanValue(p, "letterbox_detect", false);   // по умолчанию выключено
        captureMode = AmbilightController.CaptureMode.fromPref(
                PrefsUtil.rawStringValue(p, "capture_mode", AmbilightController.CaptureMode.AUTO.prefValue));
    }

    static AmbilightSettings read(SharedPreferences prefs) {
        return new AmbilightSettings(prefs);
    }

    int ledCount() {
        return ledsTop + ledsBottom + ledsLeft + ledsRight;
    }

    /** true, если раскладку зон нужно пересчитывать (тяжёлая часть). Яркость/гамма/FPS на неё не влияют. */
    boolean sameGeometry(AmbilightSettings o) {
        return o != null
                && marginX == o.marginX && marginY == o.marginY && depth == o.depth
                && ledsTop == o.ledsTop && ledsBottom == o.ledsBottom
                && ledsLeft == o.ledsLeft && ledsRight == o.ledsRight
                && direction == o.direction && offset == o.offset;
    }

    private static int getInt(SharedPreferences p, String key, int def, int min, int max) {
        int v;
        try {
            v = Integer.parseInt(PrefsUtil.rawStringValue(p, key, String.valueOf(def)).trim());
        } catch (Exception e) {
            v = def;
        }
        return Math.max(min, Math.min(max, v));
    }
}