package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

/**
 * Строит для каждого светодиода прямоугольник на уменьшенном кадре, цвет которого он должен показывать.
 * Результат: плоский массив int[], по 4 числа на светодиод: x0, y0, x1, y1 (x1/y1 не включительно).
 *
 * Порядок светодиодов: верх (слева направо) -> право (сверху вниз) -> низ (справа налево) -> лево (снизу вверх).
 */
final class ZoneMapper {
    private ZoneMapper() {}

    /**
     * @param gridW,gridH размер уменьшенного кадра
     * @param cropL,cropT,cropR,cropB сколько клеток по краям занимают чёрные полосы (0 = нет)
     */
    static int[] build(AmbilightSettings s, int gridW, int gridH, int cropL, int cropT, int cropR, int cropB) {
        final int total = s.ledCount();
        final int[] rects = new int[total * 4];
        if (total == 0) return rects;

        // Область с реальной картинкой
        final int cx0 = cropL, cy0 = cropT, cx1 = gridW - cropR, cy1 = gridH - cropB;
        final int cw = Math.max(2, cx1 - cx0), ch = Math.max(2, cy1 - cy0);

        final int mx = Math.round(cw * s.marginX);
        final int my = Math.round(ch * s.marginY);
        final int dx = Math.max(1, Math.round(cw * s.depth));
        final int dy = Math.max(1, Math.round(ch * s.depth));

        final float spanX0 = cx0 + mx, spanX1 = cx1 - mx;
        final float spanY0 = cy0 + my, spanY1 = cy1 - my;

        int n = 0;

        // Верх: слева направо
        for (int i = 0; i < s.ledsTop; i++) {
            float step = (spanX1 - spanX0) / s.ledsTop;
            put(rects, n++, gridW, gridH,
                    spanX0 + step * i, spanY0, spanX0 + step * (i + 1), spanY0 + dy);
        }
        // Право: сверху вниз
        for (int i = 0; i < s.ledsRight; i++) {
            float step = (spanY1 - spanY0) / s.ledsRight;
            put(rects, n++, gridW, gridH,
                    spanX1 - dx, spanY0 + step * i, spanX1, spanY0 + step * (i + 1));
        }
        // Низ: справа налево
        for (int i = 0; i < s.ledsBottom; i++) {
            float step = (spanX1 - spanX0) / s.ledsBottom;
            put(rects, n++, gridW, gridH,
                    spanX1 - step * (i + 1), spanY1 - dy, spanX1 - step * i, spanY1);
        }
        // Лево: снизу вверх
        for (int i = 0; i < s.ledsLeft; i++) {
            float step = (spanY1 - spanY0) / s.ledsLeft;
            put(rects, n++, gridW, gridH,
                    spanX0, spanY1 - step * (i + 1), spanX0 + dx, spanY1 - step * i);
        }

        return applyDirectionAndOffset(rects, total, s.direction == 1, s.offset);
    }

    private static void put(int[] rects, int index, int gridW, int gridH,
                            float fx0, float fy0, float fx1, float fy1) {
        int x0 = clamp(Math.round(fx0), 0, gridW - 1);
        int y0 = clamp(Math.round(fy0), 0, gridH - 1);
        int x1 = clamp(Math.round(fx1), x0 + 1, gridW);   // минимум 1 клетка
        int y1 = clamp(Math.round(fy1), y0 + 1, gridH);
        int b = index * 4;
        rects[b] = x0; rects[b + 1] = y0; rects[b + 2] = x1; rects[b + 3] = y1;
    }

    private static int[] applyDirectionAndOffset(int[] src, int count, boolean reverse, int offset) {
        if (!reverse && (offset % count) == 0) return src;
        int[] dst = new int[src.length];
        int shift = offset % count;
        for (int i = 0; i < count; i++) {
            int from = reverse ? (count - 1 - i) : i;
            int to = (i - shift + count) % count;   // сдвиг начала ленты (как в старой версии)
            System.arraycopy(src, from * 4, dst, to * 4, 4);
        }
        return dst;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}