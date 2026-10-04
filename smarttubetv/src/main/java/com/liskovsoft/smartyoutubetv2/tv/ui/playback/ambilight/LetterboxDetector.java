package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

/**
 * Находит чёрные полосы по краям кадра (фильмы 21:9 на 16:9 экране и т.п.), чтобы подсветка
 * снималась с настоящей картинки, а не гасла сверху и снизу.
 *
 * Чтобы подсветка не дёргалась, новый размер полос применяется только когда он держится стабильно:
 * полосы растут медленно (1.2 с), исчезают быстро (0.35 с).
 */
final class LetterboxDetector {
    private static final int BLACK_LEVEL = 20;          // max(R,G,B) <= этого значения считается чёрным
    private static final float ALLOWED_BRIGHT = 0.04f;  // доля «ярких» пикселей в линии (шум, логотип)
    private static final float MAX_CROP = 0.40f;        // максимум полос с каждой стороны
    private static final int MIN_CROP = 2;              // меньше клеток считаем шумом
    private static final long GROW_MS = 1200;
    private static final long SHRINK_MS = 350;

    private final int w, h;
    private final int maxRows, maxCols;

    private int appliedX, appliedY;   // применённые полосы (клеток с каждой стороны)
    private int pendingX, pendingY;
    private long pendingSince;

    LetterboxDetector(int w, int h) {
        this.w = w;
        this.h = h;
        this.maxRows = (int) (h * MAX_CROP);
        this.maxCols = (int) (w * MAX_CROP);
    }

    int cropX() { return appliedX; }
    int cropY() { return appliedY; }

    /** @return true, если применённые полосы изменились и раскладку зон нужно пересчитать */
    boolean reset() {
        boolean changed = appliedX != 0 || appliedY != 0;
        appliedX = appliedY = pendingX = pendingY = 0;
        return changed;
    }

    boolean update(int[] px, long now) {
        int top = 0;
        while (top < maxRows && isRowBlack(px, top)) top++;
        int bottom = 0;
        while (bottom < maxRows && isRowBlack(px, h - 1 - bottom)) bottom++;
        int left = 0;
        while (left < maxCols && isColBlack(px, left)) left++;
        int right = 0;
        while (right < maxCols && isColBlack(px, w - 1 - right)) right++;

        int candY = Math.min(top, bottom);
        int candX = Math.min(left, right);

        // Почти полностью чёрный кадр (затемнение, пауза между сценами) - ничего не меняем
        if (top >= maxRows && bottom >= maxRows) candY = appliedY;
        if (left >= maxCols && right >= maxCols) candX = appliedX;

        if (candY < MIN_CROP) candY = 0;
        if (candX < MIN_CROP) candX = 0;

        if (candX == appliedX && candY == appliedY) {
            pendingX = candX;
            pendingY = candY;
            pendingSince = now;
            return false;
        }
        if (candX != pendingX || candY != pendingY) {
            pendingX = candX;
            pendingY = candY;
            pendingSince = now;
            return false;
        }
        boolean shrinking = candX < appliedX || candY < appliedY;
        long required = shrinking ? SHRINK_MS : GROW_MS;
        if (now - pendingSince >= required) {
            appliedX = candX;
            appliedY = candY;
            return true;
        }
        return false;
    }

    private boolean isRowBlack(int[] px, int row) {
        int bright = 0, limit = (int) (w * ALLOWED_BRIGHT), base = row * w;
        for (int x = 0; x < w; x++) {
            if (maxChannel(px[base + x]) > BLACK_LEVEL && ++bright > limit) return false;
        }
        return true;
    }

    private boolean isColBlack(int[] px, int col) {
        int bright = 0, limit = (int) (h * ALLOWED_BRIGHT);
        for (int y = 0; y < h; y++) {
            if (maxChannel(px[y * w + col]) > BLACK_LEVEL && ++bright > limit) return false;
        }
        return true;
    }

    private static int maxChannel(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return Math.max(r, Math.max(g, b));
    }
}