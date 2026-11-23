package com.codeword.cipherai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class StarClickAccessibilityService
    extends AccessibilityService {

    private SharedPreferences prefs;
    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private int pointIndex = 0;

    private final float[][] points = {
        {0.22f, 0.22f},
        {0.42f, 0.22f},
        {0.62f, 0.22f},
        {0.78f, 0.22f},

        {0.22f, 0.40f},
        {0.42f, 0.40f},
        {0.62f, 0.40f},
        {0.78f, 0.40f},

        {0.22f, 0.58f},
        {0.42f, 0.58f},
        {0.62f, 0.58f},
        {0.78f, 0.58f},

        {0.22f, 0.74f},
        {0.42f, 0.74f},
        {0.62f, 0.74f},
        {0.78f, 0.74f}
    };

    private final Runnable clickLoop =
        new Runnable() {
            @Override
            public void run() {
                if (prefs == null) {
                    handler.postDelayed(
                        this,
                        500
                    );
                    return;
                }

                boolean enabled =
                    prefs.getBoolean(
                        "star_click_enabled",
                        false
                    );

                if (enabled) {
                    clickNextPoint();
                }

                String speed =
                    prefs.getString(
                        "star_click_speed",
                        "slow"
                    );

                long delay;

                if ("fast".equals(speed)) {
                    delay = 170;
                } else if (
                    "medium".equals(speed)
                ) {
                    delay = 300;
                } else {
                    delay = 500;
                }

                handler.postDelayed(
                    this,
                    delay
                );
            }
        };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        prefs = getSharedPreferences(
            "cipher_settings",
            MODE_PRIVATE
        );

        prefs.edit()
            .putBoolean(
                "star_accessibility_ready",
                true
            )
            .putBoolean(
                "star_click_enabled",
                false
            )
            .apply();

        handler.removeCallbacks(clickLoop);
        handler.post(clickLoop);
    }

    private void clickNextPoint() {
        DisplayMetrics metrics =
            new DisplayMetrics();

        WindowManager manager =
            (WindowManager) getSystemService(
                WINDOW_SERVICE
            );

        manager.getDefaultDisplay()
            .getRealMetrics(metrics);

        float[] point =
            points[
                pointIndex
                % points.length
            ];

        pointIndex =
            (pointIndex + 1)
            % points.length;

        float x =
            metrics.widthPixels
            * point[0];

        float y =
            metrics.heightPixels
            * point[1];

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription
            stroke =
            new GestureDescription
                .StrokeDescription(
                    path,
                    0,
                    45
                );

        GestureDescription.Builder builder =
            new GestureDescription.Builder();

        builder.addStroke(stroke);

        dispatchGesture(
            builder.build(),
            null,
            null
        );

        prefs.edit()
            .putFloat(
                "star_last_x",
                x
            )
            .putFloat(
                "star_last_y",
                y
            )
            .putLong(
                "star_click_count",
                prefs.getLong(
                    "star_click_count",
                    0
                ) + 1
            )
            .apply();
    }

    @Override
    public void onAccessibilityEvent(
        AccessibilityEvent event
    ) {
    }

    @Override
    public void onInterrupt() {
        stopClicking();
    }

    private void stopClicking() {
        if (prefs != null) {
            prefs.edit()
                .putBoolean(
                    "star_click_enabled",
                    false
                )
                .putBoolean(
                    "star_accessibility_ready",
                    false
                )
                .apply();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(clickLoop);
        stopClicking();
        super.onDestroy();
    }
}
