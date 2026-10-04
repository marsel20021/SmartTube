package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

/**
 * Подсветка WLED (Ambilight): снимает кадр видео, считает цвета светодиодов и шлёт их в WLED по DDP.
 *
 * Всё тяжёлое живёт в фоновом потоке. Раскладка зон пересчитывается ТОЛЬКО когда изменились
 * геометрические настройки (число светодиодов, отступы, направление...) или появились/исчезли чёрные полосы.
 * Остальные настройки (яркость, гамма, FPS, сглаживание...) подхватываются на лету без пересчёта.
 *
 * Публичный API не менялся: PlaybackFragment работает с классом как раньше.
 */
public class AmbilightController {
    private static final String TAG = "AmbilightController";

    /** Размер уменьшенного кадра, с которого считаются цвета. */
    private static final int CAP_W = 96;
    private static final int CAP_H = 54;
    private static final int HIGH_RES_THRESHOLD = 3000;
    private static final long IDLE_SLEEP_MS = 150L;
    private static final long ERROR_LOG_INTERVAL_MS = 5000L;

    public enum CaptureMode {
        AUTO("auto"), TEXTURE_VIEW("texture_view"), PIXEL_COPY("pixel_copy");
        public final String prefValue;
        CaptureMode(String prefValue) { this.prefValue = prefValue; }
        public static CaptureMode fromPref(String value) {
            for (CaptureMode m : values()) if (m.prefValue.equals(value)) return m;
            return AUTO;
        }
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Состояние, которое выставляет плеер (главный поток) и читает цикл
    private volatile boolean isPlaying = false;
    private volatile boolean isHdrContent = false;
    private volatile int videoWidth = 0;
    private volatile int videoHeight = 0;
    private volatile boolean isSurfaceActive = false;
    private volatile SurfaceView surfaceView = null;
    private volatile TextureView textureView = null;

    private volatile boolean settingsDirty = true;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sharedPreferences, key) -> settingsDirty = true;

    private volatile boolean isRunning = false;
    private Thread loopThread = null;

    // Состояние цикла (трогает только фоновый поток)
    private int[] rects = new int[0];
    private int ledCount = 0;
    private byte[] rgb = new byte[0];

    public AmbilightController(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    public void setPlaying(boolean playing) { this.isPlaying = playing; }
    public void setHdrContent(boolean hdr) { this.isHdrContent = hdr; }
    public void setVideoWidth(int w) { this.videoWidth = w; }
    public void setVideoHeight(int h) { this.videoHeight = h; }
    public void setSurfaceActive(boolean active) { this.isSurfaceActive = active; }

    public void attachViews(SurfaceView surface, TextureView texture) {
        this.surfaceView = surface;
        this.textureView = texture;
    }

    public synchronized void start() {
        if (isRunning) return;
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        settingsDirty = true;
        isRunning = true;
        loopThread = new Thread(this::runLoop, "AmbilightLoop");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    public synchronized void release() {
        isRunning = false;
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        if (loopThread != null) {
            loopThread.interrupt();   // остальную уборку (гашение ленты, сокет) делает сам поток
            loopThread = null;
        }
        surfaceView = null;
        textureView = null;
    }

    private void runLoop() {
        final HandlerThread copyThread = new HandlerThread("AmbilightPixelCopy");
        copyThread.start();

        final DdpSender ddp = new DdpSender();
        final FrameCapturer capturer = new FrameCapturer(mainHandler, new Handler(copyThread.getLooper()), CAP_W, CAP_H);
        final ColorProcessor colors = new ColorProcessor();
        final LetterboxDetector letterbox = new LetterboxDetector(CAP_W, CAP_H);
        final int[] pixels = new int[CAP_W * CAP_H];

        AmbilightSettings settings = AmbilightSettings.read(prefs);
        boolean remap = true;
        boolean active = false;
        long lastFrameAt = SystemClock.uptimeMillis();
        long nextTick = lastFrameAt;
        long lastErrorLog = 0;

        try {
            while (isRunning) {
                // 1. Настройки: перечитываем только если что-то менялось
                if (settingsDirty) {
                    settingsDirty = false;
                    AmbilightSettings next = AmbilightSettings.read(prefs);
                    if (!next.sameGeometry(settings)) remap = true;
                    if (settings.enabled && !next.enabled) ddp.sendBlank(next.ip, next.port, ledCount);
                    if (settings.letterboxDetect && !next.letterboxDetect && letterbox.reset()) remap = true;
                    settings = next;
                }

                // 2. Раскладка зон: только при изменении геометрии
                if (remap) {
                    remap = false;
                    rebuildMapping(settings, letterbox, colors);
                }

                // 3. Простой, пока подсветка выключена или видео на паузе
                if (!settings.enabled || !isPlaying || ledCount == 0) {
                    active = false;
                    if (!sleep(IDLE_SLEEP_MS)) break;
                    continue;
                }
                if (!active) {
                    active = true;
                    colors.snapNext();   // после паузы цвета выставляем сразу, без плавного перехода
                    nextTick = SystemClock.uptimeMillis();
                    lastFrameAt = nextTick;
                }

                // 4. Кадр -> цвета -> WLED
                Bitmap bmp = capturer.capture(resolveEngine(settings.captureMode), isHdrContent, surfaceView, textureView);
                if (bmp != null && !bmp.isRecycled()) {
                    try {
                        bmp.getPixels(pixels, 0, CAP_W, 0, 0, CAP_W, CAP_H);
                        long now = SystemClock.uptimeMillis();

                        if (settings.letterboxDetect && letterbox.update(pixels, now)) {
                            rebuildMapping(settings, letterbox, colors);
                        }

                        float dt = Math.max(1, Math.min(250, now - lastFrameAt));
                        lastFrameAt = now;
                        colors.process(pixels, CAP_W, rects, ledCount, settings, dt, rgb);
                        ddp.send(settings.ip, settings.port, rgb, ledCount);
                    } catch (Exception e) {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastErrorLog > ERROR_LOG_INTERVAL_MS) {
                            lastErrorLog = now;
                            Log.w(TAG, "Ошибка обработки кадра", e);
                        }
                    }
                }

                // 5. Фиксированный шаг: реальный FPS = заданному, время захвата учитывается
                long period = 1000L / settings.fps;
                nextTick += period;
                long wait = nextTick - SystemClock.uptimeMillis();
                if (wait > 0) {
                    if (!sleep(wait)) break;
                } else if (wait < -2 * period) {
                    nextTick = SystemClock.uptimeMillis();   // сильно отстали - не пытаемся догонять
                }
            }
        } finally {
            if (active) ddp.sendBlank(settings.ip, settings.port, ledCount);   // погасить ленту при выходе
            ddp.close();
            capturer.release();
            copyThread.quit();
        }
    }

    private void rebuildMapping(AmbilightSettings s, LetterboxDetector letterbox, ColorProcessor colors) {
        int cx = s.letterboxDetect ? letterbox.cropX() : 0;
        int cy = s.letterboxDetect ? letterbox.cropY() : 0;
        rects = ZoneMapper.build(s, CAP_W, CAP_H, cx, cy, cx, cy);
        int count = rects.length / 4;
        if (count != ledCount) {
            ledCount = count;
            rgb = new byte[count * 3];
            colors.resize(count);
        }
    }

    private FrameCapturer.Engine resolveEngine(CaptureMode mode) {
        if (isSurfaceActive) return FrameCapturer.Engine.PIXEL_COPY;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return FrameCapturer.Engine.TEXTURE_VIEW;
        switch (mode) {
            case TEXTURE_VIEW: return FrameCapturer.Engine.TEXTURE_VIEW;
            case PIXEL_COPY: return FrameCapturer.Engine.PIXEL_COPY;
            default:
                return (isHdrContent || Math.max(videoWidth, videoHeight) >= HIGH_RES_THRESHOLD)
                        ? FrameCapturer.Engine.PIXEL_COPY : FrameCapturer.Engine.TEXTURE_VIEW;
        }
    }

    /** @return false, если поток прервали (нужно выходить из цикла) */
    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}