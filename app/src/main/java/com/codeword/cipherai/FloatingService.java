package com.codeword.cipherai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {

    public static final String ACTION_STOP =
        "com.codeword.cipherai.STOP";

    public static final String ACTION_REFRESH =
        "com.codeword.cipherai.REFRESH";

    private static final String CHANNEL_ID = "cipher_ai_service";
    private static final int NOTIFICATION_ID = 729;

    private WindowManager windowManager;
    private LinearLayout container;
    private TextView ball;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private TextView stateView;

    private float touchX;
    private float touchY;
    private int startX;
    private int startY;
    private boolean moved;

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(
            "cipher_settings",
            MODE_PRIVATE
        );

        createChannel();
        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        );

        createFloatingView();
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_STOP.equals(action)) {
                shutdown();
                return START_NOT_STICKY;
            }

            if (ACTION_REFRESH.equals(action)) {
                applyBallSize();
            }
        }

        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                new NotificationChannel(
                    CHANNEL_ID,
                    "暗号AI运行状态",
                    NotificationManager.IMPORTANCE_LOW
                );

            channel.setDescription(
                "用于显示暗号AI的停止按钮"
            );

            NotificationManager manager =
                getSystemService(
                    NotificationManager.class
                );

            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent =
            new Intent(this, MainActivity.class);

        PendingIntent openPending =
            PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                    | PendingIntent.FLAG_IMMUTABLE
            );

        Intent stopIntent =
            new Intent(this, FloatingService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPending =
            PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                    | PendingIntent.FLAG_IMMUTABLE
            );

        Notification.Builder builder =
            Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(
                    this,
                    CHANNEL_ID
                )
                : new Notification.Builder(this);

        return builder
            .setContentTitle("暗号AI悬浮球正在运行")
            .setContentText("点击“立即停止”可彻底关闭")
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(
                new Notification.Action.Builder(
                    null,
                    "立即停止",
                    stopPending
                ).build()
            )
            .build();
    }

    private GradientDrawable background(
        int color,
        int radius
    ) {
        GradientDrawable drawable =
            new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(
            2,
            Color.rgb(88, 230, 255)
        );

        return drawable;
    }

    private Button panelButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);

        LinearLayout.LayoutParams layout =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        layout.setMargins(8, 6, 8, 6);
        button.setLayoutParams(layout);
        return button;
    }

    private void createFloatingView() {
        windowManager =
            (WindowManager) getSystemService(
                WINDOW_SERVICE
            );

        container = new LinearLayout(this);
        container.setOrientation(
            LinearLayout.VERTICAL
        );
        container.setGravity(Gravity.END);

        ball = new TextView(this);
        ball.setText("暗号AI");
        ball.setTextColor(Color.WHITE);
        ball.setTextSize(12);
        ball.setGravity(Gravity.CENTER);
        ball.setBackground(
            background(
                Color.rgb(84, 74, 190),
                200
            )
        );

        container.addView(ball);

        panel = new LinearLayout(this);
        panel.setOrientation(
            LinearLayout.VERTICAL
        );
        panel.setPadding(14, 14, 14, 14);
        panel.setVisibility(View.GONE);
        panel.setBackground(
            background(
                Color.rgb(18, 25, 39),
                28
            )
        );

        stateView = new TextView(this);
        stateView.setText(
            "持续观察：尚未接入\n" +
            "自动操作：尚未接入\n" +
            "当前为安全悬浮球测试版"
        );
        stateView.setTextColor(Color.WHITE);
        stateView.setTextSize(13);
        stateView.setPadding(12, 12, 12, 12);
        panel.addView(stateView);
        updateCaptureStatus();

        Button small = panelButton("小");
        Button medium = panelButton("中");
        Button large = panelButton("大");
        Button pause = panelButton("暂停显示");
        Button stop =
            panelButton("■ 停止并退出悬浮服务");

        stop.setTextColor(Color.rgb(190, 0, 30));

        panel.addView(small);
        panel.addView(medium);
        panel.addView(large);
        panel.addView(pause);
        panel.addView(stop);

        container.addView(panel);

        small.setOnClickListener(v -> setSize("small"));
        medium.setOnClickListener(v -> setSize("medium"));
        large.setOnClickListener(v -> setSize("large"));

        pause.setOnClickListener(v ->
            panel.setVisibility(View.GONE)
        );

        stop.setOnClickListener(v -> shutdown());

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        params.gravity =
            Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 300;

        ball.setOnTouchListener(
            (view, event) -> handleTouch(event)
        );

        applyBallSize();
        windowManager.addView(container, params);
    }

    private boolean handleTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getRawX();
                touchY = event.getRawY();
                startX = params.x;
                startY = params.y;
                moved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                int dx =
                    (int) (event.getRawX() - touchX);
                int dy =
                    (int) (event.getRawY() - touchY);

                if (Math.abs(dx) + Math.abs(dy) > 8) {
                    moved = true;
                }

                params.x = startX + dx;
                params.y = startY + dy;

                if (windowManager != null
                    && container != null) {
                    windowManager.updateViewLayout(
                        container,
                        params
                    );
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved) {
                    panel.setVisibility(
                        panel.getVisibility()
                            == View.VISIBLE
                            ? View.GONE
                            : View.VISIBLE
                    );
                }
                return true;

            default:
                return false;
        }
    }


    private void updateCaptureStatus() {
        if (stateView == null) {
            return;
        }

        boolean active =
            prefs.getBoolean(
                "capture_active",
                false
            );

        float fps =
            prefs.getFloat(
                "capture_fps",
                0f
            );

        String size =
            prefs.getString(
                "ball_size",
                "small"
            );

        String sizeName =
            "small".equals(size)
                ? "小"
                : "medium".equals(size)
                    ? "中"
                    : "大";

        stateView.setText(
            "持续观察："
                + (
                    active
                        ? "运行中"
                        : "已停止"
                )
                + "\n采集帧率："
                + String.format(
                    "%.1f FPS",
                    fps
                )
                + "\n运行规模："
                + sizeName
                + "\n自动操作：尚未启用"
        );
    }

    private void setSize(String size) {
        prefs.edit()
            .putString("ball_size", size)
            .apply();
        applyBallSize();
    }

    private void applyBallSize() {
        if (ball == null) {
            return;
        }

        String size =
            prefs.getString(
                "ball_size",
                "small"
            );

        int dp;

        if ("large".equals(size)) {
            dp = 74;
        } else if ("medium".equals(size)) {
            dp = 60;
        } else {
            dp = 48;
        }

        int pixels =
            (int) (
                dp
                * getResources()
                    .getDisplayMetrics()
                    .density
            );

        LinearLayout.LayoutParams ballParams =
            new LinearLayout.LayoutParams(
                pixels,
                pixels
            );

        ball.setLayoutParams(ballParams);
    }

    private void shutdown() {
        Intent captureStop =
            new Intent(
                this,
                CaptureService.class
            );

        captureStop.setAction(
            CaptureService.ACTION_STOP
        );

        startService(captureStop);

        if (windowManager != null
            && container != null) {
            try {
                windowManager.removeView(container);
            } catch (Exception ignored) {
            }
        }

        container = null;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (windowManager != null
            && container != null) {
            try {
                windowManager.removeView(container);
            } catch (Exception ignored) {
            }
        }

        container = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
