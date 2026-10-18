package com.emanuelef.remote_capture.radar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.emanuelef.remote_capture.CaptureHelper;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.activities.MainActivity;
import com.emanuelef.remote_capture.model.CaptureSettings;

public class RadarActivity extends AppCompatActivity {
    private static final String ALBION_PKG = "com.albiononline";
    private static final String RADAR_URL = "http://127.0.0.1:5001/";

    private CaptureHelper capHelper;
    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retries = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        capHelper = new CaptureHelper(this);
        capHelper.setListener(ok -> toast(ok ? "Захват запущен" : "Не удалось запустить захват"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        LinearLayout row1 = new LinearLayout(this);
        row1.addView(button("Старт", v -> startAll()), weight());
        row1.addView(button("Стоп", v -> stopAll()), weight());
        row1.addView(button("Обновить", v -> reload()), weight());
        root.addView(row1, wrap());

        LinearLayout row2 = new LinearLayout(this);
        row2.addView(button("Оверлей", v -> startOverlay()), weight());
        row2.addView(button("PCAPdroid", v -> startActivity(new Intent(this, MainActivity.class))), weight());
        row2.addView(button("Лог", v -> showLog()), weight());
        root.addView(row2, wrap());

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame() && retries++ < 20)
                    handler.postDelayed(() -> v.loadUrl(RADAR_URL), 1000);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        if (getIntent().getBooleanExtra("autostart", false)) handler.post(this::startAll);
        if (getIntent().getBooleanExtra("overlay", false)) handler.post(this::startOverlay);
        if (RadarRunner.isRunning()) reload();
        if (getIntent().getBooleanExtra("overlay", false)) handler.post(this::startOverlay);
    }

    private void startAll() {
        String err = RadarRunner.start(this);
        if (err != null) {
            toast("Радар не запустился: " + err);
            return;
        }
        Intent i = new Intent();
        i.putExtra("pcap_dump_mode", "udp_exporter");
        i.putExtra("collector_host", "127.0.0.1");
        i.putExtra("collector_port", "1234");
        i.putExtra("app_filter", ALBION_PKG);
        capHelper.startCapture(new CaptureSettings(this, i));
        handler.postDelayed(this::reload, 1500);
    }

    private void stopAll() {
        stopService(new Intent(this, OverlayService.class));
        CaptureService.stopService();
        RadarRunner.stop();
        toast("Остановлено");
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Разреши показ поверх других приложений и вернись назад");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!RadarRunner.isRunning()) startAll();
        ContextCompat.startForegroundService(this, new Intent(this, OverlayService.class));
        toast("Оверлей включён. Открой игру");
    }

    private void reload() {
        retries = 0;
        web.loadUrl(RADAR_URL);
    }

    private void showLog() {
        TextView t = new TextView(this);
        t.setText(RadarRunner.readLog(this));
        t.setTextIsSelectable(true);
        t.setPadding(24, 24, 24, 24);
        new AlertDialog.Builder(this).setTitle("Лог радара").setView(t)
                .setPositiveButton("OK", null).show();
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
