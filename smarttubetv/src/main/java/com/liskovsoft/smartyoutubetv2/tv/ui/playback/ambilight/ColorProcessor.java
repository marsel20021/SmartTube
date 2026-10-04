package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

/**
 * Превращает кадр в цвета светодиодов:
 * усреднение области -> гашение тёмных зон -> насыщенность -> сглаживание -> гамма/яркость.
 *
 * Сглаживание: быстро реагирует на рост яркости, плавно гаснет, а при резкой смене сцены
 * (большая разница с текущим состоянием) переключается мгновенно. Скорость не зависит от FPS.
 */
final class ColorProcessor {
    private static final float REF_FRAME_MS = 1000f / 30f;  // smoothing задан «на кадр при 30 fps»
    private static final float SCENE_CUT_LUMA = 70f;         // средняя разница яркости = смена сцены
    private static final float ATTACK_BOOST = 2f;            // рост яркости быстрее затухания

    private float[] current = new float[0];
    private float[] target = new float[0];
    private final byte[] gammaLut = new byte[256];
    private double lutGamma = -1;
    private float lutBrightness = -1;
    private boolean snap = true;

    void resize(int ledCount) {
        current = new float[ledCount * 3];
        target = new float[ledCount * 3];
        snap = true;
    }

    /** Следующий кадр применить сразу, без плавного перехода (после паузы/включения). */
    void snapNext() {
        snap = true;
    }

    void process(int[] px, int gridW, int[] rects, int ledCount, AmbilightSettings s, float dtMs, byte[] out) {
        if (ledCount <= 0 || current.length != ledCount * 3) return;
        ensureLut(s.gamma, s.brightness);

        // Проход 1: целевые цвета
        final int bt = s.blackThreshold;
        final float sat = s.saturation;
        float diffSum = 0f;
        for (int i = 0; i < ledCount; i++) {
            int rb = i * 4;
            int x0 = rects[rb], y0 = rects[rb + 1], x1 = rects[rb + 2], y1 = rects[rb + 3];
            int rs = 0, gs = 0, bs = 0;
            for (int y = y0; y < y1; y++) {
                int idx = y * gridW + x0;
                for (int x = x0; x < x1; x++, idx++) {
                    int c = px[idx];
                    rs += (c >> 16) & 0xFF;
                    gs += (c >> 8) & 0xFF;
                    bs += c & 0xFF;
                }
            }
            float area = (x1 - x0) * (y1 - y0);
            float r = rs / area, g = gs / area, b = bs / area;

            if (s.blackout && r < bt && g < bt && b < bt) {
                r = g = b = 0f;
            }
            if (sat != 1f) {
                float y = luma(r, g, b);
                r = clamp255(y + (r - y) * sat);
                g = clamp255(y + (g - y) * sat);
                b = clamp255(y + (b - y) * sat);
            }

            int t = i * 3;
            target[t] = r;
            target[t + 1] = g;
            target[t + 2] = b;
            diffSum += Math.abs(luma(r, g, b) - luma(current[t], current[t + 1], current[t + 2]));
        }

        // Проход 2: сглаживание и вывод
        final boolean cut = snap || (diffSum / ledCount) > SCENE_CUT_LUMA;
        snap = false;
        final float frames = Math.max(0.05f, dtMs / REF_FRAME_MS);
        final float alphaDown = alpha(s.smoothing, frames);
        final float alphaUp = alpha(Math.min(1f, s.smoothing * ATTACK_BOOST), frames);

        for (int i = 0; i < ledCount; i++) {
            int t = i * 3;
            if (cut) {
                current[t] = target[t];
                current[t + 1] = target[t + 1];
                current[t + 2] = target[t + 2];
            } else {
                float a = luma(target[t], target[t + 1], target[t + 2]) > luma(current[t], current[t + 1], current[t + 2])
                        ? alphaUp : alphaDown;
                current[t] += (target[t] - current[t]) * a;
                current[t + 1] += (target[t + 1] - current[t + 1]) * a;
                current[t + 2] += (target[t + 2] - current[t + 2]) * a;
            }
            out[t] = gammaLut[(int) (current[t] + 0.5f) & 0xFF];
            out[t + 1] = gammaLut[(int) (current[t + 1] + 0.5f) & 0xFF];
            out[t + 2] = gammaLut[(int) (current[t + 2] + 0.5f) & 0xFF];
        }
    }

    private void ensureLut(double gamma, float brightness) {
        if (gamma == lutGamma && brightness == lutBrightness) return;
        for (int i = 0; i < 256; i++) {
            int v = (int) (Math.pow(i / 255.0, gamma) * brightness * 255);
            gammaLut[i] = (byte) Math.max(0, Math.min(255, v));
        }
        lutGamma = gamma;
        lutBrightness = brightness;
    }

    /** Коэффициент сглаживания для кадра длиной dt (в долях эталонного кадра 30 fps). */
    private static float alpha(float perRefFrame, float frames) {
        if (perRefFrame >= 1f) return 1f;
        return 1f - (float) Math.pow(1f - perRefFrame, frames);
    }

    private static float luma(float r, float g, float b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static float clamp255(float v) {
        return v < 0f ? 0f : (v > 255f ? 255f : v);
    }
}