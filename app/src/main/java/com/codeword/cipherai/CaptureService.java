package com.codeword.cipherai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class CaptureService extends Service {

    public static final String ACTION_STOP =
        "com.codeword.cipherai.CAPTURE_STOP";

    public static final String EXTRA_CODE =
        "capture_code";

    public static final String EXTRA_DATA =
        "capture_data";

    private static final String CHANNEL =
        "cipher_capture";

    private static final int NOTICE = 730;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private SharedPreferences prefs;

    private long frames;
    private long lastTime;

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(
            "cipher_settings",
            MODE_PRIVATE
        );

        createChannel();
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }

        startForeground(
            NOTICE,
            notification("正在准备持续观察")
        );

        int code = intent.getIntExtra(
            EXTRA_CODE,
            0
        );

        Intent data;

        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                EXTRA_DATA,
                Intent.class
            );
        } else {
            data = intent.getParcelableExtra(
                EXTRA_DATA
            );
        }

        if (code == 0 || data == null) {
            stopEverything();
            return START_NOT_STICKY;
        }

        startCapture(code, data);
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL,
                "暗号AI持续观察",
                NotificationManager.IMPORTANCE_LOW
            );

        getSystemService(
            NotificationManager.class
        ).createNotificationChannel(channel);
    }

    private Notification notification(String message) {
        Intent stop =
            new Intent(this, CaptureService.class);

        stop.setAction(ACTION_STOP);

        PendingIntent pending =
            PendingIntent.getService(
                this,
                730,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT
                    | PendingIntent.FLAG_IMMUTABLE
            );

        Notification.Builder builder =
            Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(
                    this,
                    CHANNEL
                )
                : new Notification.Builder(this);

        return builder
            .setContentTitle("暗号AI持续观察")
            .setContentText(message)
            .setSmallIcon(
                android.R.drawable.ic_menu_view
            )
            .setOngoing(true)
            .addAction(
                new Notification.Action.Builder(
                    null,
                    "立即停止",
                    pending
                ).build()
            )
            .build();
    }

    private void startCapture(
        int code,
        Intent data
    ) {
        releaseCapture();

        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(
                MEDIA_PROJECTION_SERVICE
            );

        projection =
            manager.getMediaProjection(code, data);

        if (projection == null) {
            stopEverything();
            return;
        }

        projection.registerCallback(
            new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    releaseCapture();
                }
            },
            new Handler(getMainLooper())
        );

        DisplayMetrics metrics =
            new DisplayMetrics();

        WindowManager window =
            (WindowManager) getSystemService(
                WINDOW_SERVICE
            );

        window.getDefaultDisplay()
            .getRealMetrics(metrics);

        String size = prefs.getString(
            "ball_size",
            "small"
        );

        int width =
            "large".equals(size)
                ? 720
                : "medium".equals(size)
                    ? 540
                    : 360;

        int height = Math.max(
            1,
            Math.round(
                metrics.heightPixels
                * width
                / (float) metrics.widthPixels
            )
        );

        reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        );

        thread =
            new HandlerThread("CipherCapture");

        thread.start();
        handler = new Handler(thread.getLooper());

        frames = 0;
        lastTime = System.currentTimeMillis();

        reader.setOnImageAvailableListener(
            source -> {
                Image image = null;

                try {
                    image =
                        source.acquireLatestImage();

                    if (image != null) {
                        frames++;
                        updateFps();
                    }
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            },
            handler
        );

        display = projection.createVirtualDisplay(
            "CipherAI",
            width,
            height,
            metrics.densityDpi,
            DisplayManager
                .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(),
            null,
            handler
        );

        prefs.edit()
            .putBoolean("capture_active", true)
            .putFloat("capture_fps", 0f)
            .apply();
    }

    private void updateFps() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;

        if (elapsed < 1000) {
            return;
        }

        float fps =
            frames * 1000f / elapsed;

        prefs.edit()
            .putFloat("capture_fps", fps)
            .apply();

        NotificationManager manager =
            (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE
            );

        manager.notify(
            NOTICE,
            notification(
                String.format(
                    "持续观察中：%.1f FPS",
                    fps
                )
            )
        );

        frames = 0;
        lastTime = now;
    }

    private void releaseCapture() {
        if (display != null) {
            display.release();
            display = null;
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }

        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
            }

            projection = null;
        }

        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }

        handler = null;

        if (prefs != null) {
            prefs.edit()
                .putBoolean(
                    "capture_active",
                    false
                )
                .putFloat(
                    "capture_fps",
                    0f
                )
                .apply();
        }
    }

    private void stopEverything() {
        releaseCapture();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releaseCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
