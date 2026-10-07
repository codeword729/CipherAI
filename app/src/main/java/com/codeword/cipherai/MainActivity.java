package com.codeword.cipherai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private LinearLayout root;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("cipher_settings", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                100
            );
        }

        buildUi();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(8, 12, 8, 12);
        return view;
    }

    private Button button(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(16);
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        return button;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(8, 11, 18));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 55, 34, 55);
        scroll.addView(root);

        TextView title = text("暗号AI", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = text(
            "我可以帮你操作\n第一版安全悬浮球测试",
            17,
            Color.rgb(168, 184, 210)
        );
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        TextView warning = text(
            "当前版本不会读取游戏画面，也不会点击游戏技能。" +
            "这一版只测试悬浮球、大小设置、暂停和彻底关闭，" +
            "避免出现无法关闭的问题。",
            14,
            Color.rgb(255, 196, 105)
        );
        root.addView(warning);

        Button permission = button("① 授予悬浮窗权限");
        permission.setOnClickListener(v -> requestOverlay());
        root.addView(permission);

        Button start = button("② 启动暗号AI悬浮球");
        start.setOnClickListener(v -> startFloating());
        root.addView(start);

        root.addView(text("悬浮球大小", 18, Color.WHITE));

        LinearLayout sizes = new LinearLayout(this);
        sizes.setOrientation(LinearLayout.HORIZONTAL);

        Button small = new Button(this);
        Button medium = new Button(this);
        Button large = new Button(this);

        small.setText("小");
        medium.setText("中");
        large.setText("大");

        sizes.addView(
            small,
            new LinearLayout.LayoutParams(0, 120, 1)
        );
        sizes.addView(
            medium,
            new LinearLayout.LayoutParams(0, 120, 1)
        );
        sizes.addView(
            large,
            new LinearLayout.LayoutParams(0, 120, 1)
        );

        root.addView(sizes);

        small.setOnClickListener(v -> saveSize("small"));
        medium.setOnClickListener(v -> saveSize("medium"));
        large.setOnClickListener(v -> saveSize("large"));

        Button stop = button("■ 完全停止悬浮服务");
        stop.setTextColor(Color.rgb(190, 0, 30));
        stop.setOnClickListener(v -> {
            Intent intent =
                new Intent(this, FloatingService.class);
            intent.setAction(FloatingService.ACTION_STOP);
            startService(intent);
            Toast.makeText(
                this,
                "已经发送完全停止指令",
                Toast.LENGTH_SHORT
            ).show();
        });
        root.addView(stop);

        TextView safety = text(
            "关闭方法：\n" +
            "1. 悬浮球面板里的红色停止按钮；\n" +
            "2. 暗号AI通知栏里的立即停止；\n" +
            "3. 当前主界面的完全停止按钮；\n" +
            "4. 系统设置中强制停止应用。",
            14,
            Color.rgb(155, 170, 195)
        );
        root.addView(safety);

        setContentView(scroll);
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "悬浮窗权限已经开启",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "请先授予悬浮窗权限",
                Toast.LENGTH_LONG
            ).show();
            requestOverlay();
            return;
        }

        Intent intent =
            new Intent(this, FloatingService.class);

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(
            this,
            "悬浮球已经启动",
            Toast.LENGTH_SHORT
        ).show();
    }

    private void saveSize(String size) {
        prefs.edit().putString("ball_size", size).apply();

        Intent intent =
            new Intent(this, FloatingService.class);
        intent.setAction(FloatingService.ACTION_REFRESH);
        startService(intent);

        String name = size.equals("small")
            ? "小"
            : size.equals("medium") ? "中" : "大";

        Toast.makeText(
            this,
            "已经设置为" + name,
            Toast.LENGTH_SHORT
        ).show();
    }
}
