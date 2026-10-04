package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Снимает уменьшенный кадр видео в переиспользуемый Bitmap.
 * Вызывается из фонового потока и ждёт результата (TextureView - через главный поток, PixelCopy - через свой Handler).
 */
final class FrameCapturer {
    enum Engine { TEXTURE_VIEW, PIXEL_COPY }

    private static final long TEXTURE_TIMEOUT_MS = 200L;
    private static final long PIXEL_COPY_TIMEOUT_MS = 500L;

    private final Handler mainHandler;
    private final Handler copyHandler;
    private final int width, height;
    private Bitmap bitmap;

    FrameCapturer(Handler mainHandler, Handler copyHandler, int width, int height) {
        this.mainHandler = mainHandler;
        this.copyHandler = copyHandler;
        this.width = width;
        this.height = height;
    }

    /** @return Bitmap с кадром или null, если снять не удалось. Bitmap принадлежит захватчику, его нельзя хранить. */
    @SuppressLint("NewApi")   // версия API проверяется внутри метода
    Bitmap capture(Engine engine, boolean hdr, SurfaceView surfaceView, TextureView textureView) {
        // RGBA_F16 нужен только для HDR через PixelCopy, во всех остальных случаях хватает ARGB_8888
        Bitmap.Config config = (hdr && engine == Engine.PIXEL_COPY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? Bitmap.Config.RGBA_F16 : Bitmap.Config.ARGB_8888;
        final Bitmap bmp = ensureBitmap(config);

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};

        if (engine == Engine.TEXTURE_VIEW) {
            mainHandler.post(() -> {
                try {
                    if (textureView != null && !bmp.isRecycled()) {
                        textureView.getBitmap(bmp);
                        ok[0] = true;
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
            return await(latch, TEXTURE_TIMEOUT_MS) && ok[0] ? bmp : null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null;   // PixelCopy появился в API 24
        mainHandler.post(() -> {
            try {
                if (surfaceView != null && surfaceView.getHolder().getSurface().isValid() && !bmp.isRecycled()) {
                    PixelCopy.request(surfaceView, bmp, result -> {
                        ok[0] = (result == PixelCopy.SUCCESS);
                        latch.countDown();
                    }, copyHandler);
                } else {
                    latch.countDown();
                }
            } catch (Exception e) {
                latch.countDown();
            }
        });
        return await(latch, PIXEL_COPY_TIMEOUT_MS) && ok[0] ? bmp : null;
    }

    void release() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    private Bitmap ensureBitmap(Bitmap.Config config) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getConfig() != config) {
            // Старый не recycle(): на него может ещё ссылаться запоздавший запрос, сборщик справится сам
            bitmap = Bitmap.createBitmap(width, height, config);
        }
        return bitmap;
    }

    private static boolean await(CountDownLatch latch, long timeoutMs) {
        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}