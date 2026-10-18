package com.emanuelef.remote_capture.radar;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {
    public static volatile boolean running = false;
    public static final String ACTION_STOP = "com.emanuelef.remote_capture.radar.STOP_OVERLAY";
    private static final String CHANNEL = "radar_overlay";
    private static final String URL = "http://127.0.0.1:5001/?overlay=1";
    private static final float[] ALPHAS = {0.8f, 0.65f, 0.5f, 0.35f};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences prefs;
    private LinearLayout bar;
    private FrameLayout radarBox;
    private WebView web;
    private WindowManager.LayoutParams barLp, radarLp;
    private TextView lockBtn, foldBtn;
    private float density;
    private int barH, sizeDp, alphaIdx, retries = 0;
    private boolean locked = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNow();
        running = true;
        if (bar == null) {
            try {
                createWindows();
            } catch (Exception e) {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNow() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Radar overlay",
                    NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Albion Radar")
                .setContentText("Оверлей включён")
                .addAction(0, "Закрыть", pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1701, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1701, n);
    }

    @SuppressWarnings("deprecation")
    private static int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    private void createWindows() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("radar_overlay", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        barH = (int) (40 * density);
        sizeDp = prefs.getInt("size", 260);
        alphaIdx = Math.min(prefs.getInt("alpha", 1), ALPHAS.length - 1);
        int x = prefs.getInt("x", 20);
        int y = prefs.getInt("y", 100);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0182028);
        bg.setCornerRadius(10 * density);
        bar.setBackground(bg);
        int pad = (int) (6 * density);
        bar.setPadding(pad, 0, pad, 0);

        TextView title = btn("≡ Радар", null);
        bar.addView(title);
        bar.addView(btn("−", v -> resize(-1)));
        bar.addView(btn("+", v -> resize(1)));
        bar.addView(btn("◐", v -> cycleAlpha()));
        lockBtn = btn("✋", v -> toggleLock());
        bar.addView(lockBtn);
        foldBtn = btn("▾", v -> toggleFold());
        bar.addView(foldBtn);
        bar.addView(btn("✕", v -> stopSelf()));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        barLp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, barH,
                overlayType(), flags, PixelFormat.TRANSLUCENT);
        barLp.gravity = Gravity.TOP | Gravity.START;
        barLp.x = x;
        barLp.y = y;

        radarBox = new FrameLayout(this);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setBackgroundColor(0x00000000);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame() && retries++ < 40)
                    handler.postDelayed(() -> v.loadUrl(URL), 1000);
            }
        });
        radarBox.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(URL);

        int px = (int) (sizeDp * density);
        radarLp = new WindowManager.LayoutParams(px, px, overlayType(), flags, PixelFormat.TRANSLUCENT);
        radarLp.gravity = Gravity.TOP | Gravity.START;
        radarLp.x = x;
        radarLp.y = y + barH;
        radarLp.alpha = ALPHAS[alphaIdx];

        title.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX();
                        sy = e.getRawY();
                        ox = barLp.x;
                        oy = barLp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        barLp.x = Math.max(0, ox + (int) (e.getRawX() - sx));
                        barLp.y = Math.max(0, oy + (int) (e.getRawY() - sy));
                        applyPos();
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("x", barLp.x).putInt("y", barLp.y).apply();
                        return true;
                }
                return false;
            }
        });

        wm.addView(radarBox, radarLp);
        wm.addView(bar, barLp);
    }

    private TextView btn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(17);
        int p = (int) (9 * density);
        t.setPadding(p, 0, p, 0);
        t.setGravity(Gravity.CENTER);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    private void applyPos() {
        radarLp.x = barLp.x;
        radarLp.y = barLp.y + barH;
        wm.updateViewLayout(bar, barLp);
        wm.updateViewLayout(radarBox, radarLp);
    }

    private void resize(int dir) {
        sizeDp = Math.max(140, Math.min(520, sizeDp + dir * 40));
        int px = (int) (sizeDp * density);
        radarLp.width = px;
        radarLp.height = px;
        wm.updateViewLayout(radarBox, radarLp);
        prefs.edit().putInt("size", sizeDp).apply();
    }

    private void cycleAlpha() {
        alphaIdx = (alphaIdx + 1) % ALPHAS.length;
        radarLp.alpha = ALPHAS[alphaIdx];
        wm.updateViewLayout(radarBox, radarLp);
        prefs.edit().putInt("alpha", alphaIdx).apply();
    }

    private void toggleLock() {
        locked = !locked;
        if (locked) radarLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else radarLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lockBtn.setText(locked ? "🎮" : "✋");
        wm.updateViewLayout(radarBox, radarLp);
    }

    private void toggleFold() {
        boolean hide = radarBox.getVisibility() == View.VISIBLE;
        radarBox.setVisibility(hide ? View.GONE : View.VISIBLE);
        foldBtn.setText(hide ? "▸" : "▾");
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (bar != null) wm.removeView(bar);
            if (radarBox != null) wm.removeView(radarBox);
        } catch (Exception ignored) {
        }
        if (web != null) web.destroy();
        bar = null;
        super.onDestroy();
    }
}
