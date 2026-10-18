package com.emanuelef.remote_capture.radar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.emanuelef.remote_capture.CaptureHelper;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.R;
import com.emanuelef.remote_capture.model.CaptureSettings;
import com.google.android.material.tabs.TabLayout;

public class RadarFragment extends Fragment {
    private static final String ALBION_PKG = "com.albiononline";
    private static final String RADAR_URL = "http://127.0.0.1:5001/";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int BG = 0xFF0A0A0D, CARD = 0xFF14141A, LINE = 0xFF2A2A33;
    private static final int RED = 0xFFE5334B, TEXT = 0xFFF2F2F5, MUTED = 0xFF9A9AA6, OK = 0xFF3DDC84;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private float d = 1f;
    private View radarDot, captureDot;
    private TextView radarState, captureState, mainBtn, overlayVal, previewToggle;
    private LinearLayout overlayTile, previewBox;
    private WebView web;
    private boolean previewOn = false;
    private String currentUrl = RADAR_URL;
    private int retries = 0;
    private Boolean lastActive = null, lastOverlay = null;

    private int dp(int v) { return Math.round(v * d); }

    private GradientDrawable shape(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private Drawable ripple(GradientDrawable shape) {
        return new RippleDrawable(ColorStateList.valueOf(0x33E5334B), shape, null);
    }

    private TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        l.setBackground(shape(CARD, 14, LINE, 1));
        return l;
    }

    private View dot(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(10), dp(10));
        p.rightMargin = dp(10);
        v.setLayoutParams(p);
        return v;
    }

    private void paintDot(View v, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? OK : 0xFF55555F);
        v.setBackground(g);
    }

    private LinearLayout statusRow(Context c, View dot, String name, TextView value) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        r.addView(dot);
        r.addView(text(c, name, 14, MUTED, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.addView(value);
        return r;
    }

    private TextView chip(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 13, TEXT, false);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(ripple(shape(BG, 20, LINE, 1)));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private TextView outlineButton(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(12), dp(8), dp(12));
        t.setBackground(ripple(shape(0x00000000, 12, RED, 1)));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout tile(Context c, String icon, String title, TextView valueView, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(shape(CARD, 14, LINE, 1)));
        t.setClickable(true);
        t.setOnClickListener(l);
        TextView ic = text(c, icon, 22, RED, true);
        ic.setGravity(Gravity.CENTER);
        t.addView(ic);
        TextView tt = text(c, title, 13, TEXT, true);
        tt.setGravity(Gravity.CENTER);
        t.addView(tt);
        if (valueView != null) {
            valueView.setGravity(Gravity.CENTER);
            t.addView(valueView);
        }
        return t;
    }

    private LinearLayout.LayoutParams tileLp(boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(84), 1f);
        if (first) p.rightMargin = dp(6); else p.leftMargin = dp(6);
        return p;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final Context c = requireContext();
        d = getResources().getDisplayMetrics().density;
        lastActive = null;
        lastOverlay = null;

        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        TextView title = text(c, "ALBION RADAR", 22, TEXT, true);
        title.setLetterSpacing(0.1f);
        col.addView(title);
        col.addView(text(c, "захват трафика · радар · оверлей", 12, MUTED, false));

        LinearLayout status = card(c);
        radarDot = dot(c);
        radarState = text(c, "", 14, TEXT, true);
        captureDot = dot(c);
        captureState = text(c, "", 14, TEXT, true);
        status.addView(statusRow(c, radarDot, "Радар", radarState));
        status.addView(statusRow(c, captureDot, "Захват трафика", captureState));
        col.addView(status, lp(MATCH, WRAP, 14));

        mainBtn = text(c, "", 16, Color.WHITE, true);
        mainBtn.setGravity(Gravity.CENTER);
        mainBtn.setLetterSpacing(0.06f);
        mainBtn.setClickable(true);
        mainBtn.setOnClickListener(v -> {
            if (RadarRunner.isRunning() || CaptureService.isServiceActive()) stopAll(); else startAll();
        });
        col.addView(mainBtn, lp(MATCH, dp(58), 14));

        LinearLayout row1 = new LinearLayout(c);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        overlayVal = text(c, "", 12, MUTED, false);
        overlayTile = tile(c, "◱", "Оверлей", overlayVal, v -> toggleOverlay());
        row1.addView(overlayTile, tileLp(true));
        row1.addView(tile(c, "⛶", "На весь экран", null,
                v -> startActivity(new Intent(c, RadarActivity.class))), tileLp(false));
        col.addView(row1, lp(MATCH, WRAP, 12));

        LinearLayout row2 = new LinearLayout(c);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(tile(c, "⚙", "Настройки и фильтры", null, v -> showPage("settings")), tileLp(true));
        row2.addView(tile(c, "☰", "Лог радара", null, v -> showLog()), tileLp(false));
        col.addView(row2, lp(MATCH, WRAP, 12));

        LinearLayout work = card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(c, "РАБОЧАЯ ОБЛАСТЬ", 12, MUTED, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        previewToggle = chip(c, "Показать", v -> togglePreview());
        head.addView(previewToggle);
        work.addView(head);

        previewBox = new LinearLayout(c);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setVisibility(View.GONE);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[][] pages = {{"Карта", ""}, {"Игроки", "players"}, {"Ресурсы", "resources"},
                {"Мобы", "enemies"}, {"Сундуки", "chests"}, {"Настройки", "settings"}};
        for (String[] pg : pages) {
            final String path = pg[1];
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, WRAP);
            cp.rightMargin = dp(8);
            chips.addView(chip(c, pg[0], v -> showPage(path)), cp);
        }
        hs.addView(chips);
        previewBox.addView(hs, lp(MATCH, WRAP, 0));
        work.addView(previewBox, lp(MATCH, WRAP, 10));
        col.addView(work, lp(MATCH, WRAP, 12));

        LinearLayout sw = new LinearLayout(c);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, WRAP, 1f);
        a.rightMargin = dp(6);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, WRAP, 1f);
        b.leftMargin = dp(6);
        sw.addView(outlineButton(c, "PCAPdroid · Состояние", v -> selectMainTab(0)), a);
        sw.addView(outlineButton(c, "PCAPdroid · Соединения", v -> selectMainTab(1)), b);
        col.addView(sw, lp(MATCH, WRAP, 12));

        LinearLayout hint = card(c);
        hint.addView(text(c, "КАРТА ПУСТАЯ?", 12, MUTED, true));
        TextView hintText = text(c, "1. Нажми «Запустить» и разреши VPN.\n"
                + "2. Зайди в игру персонажем уже после запуска.\n"
                + "3. Открой «Лог радара»: пакеты должны идти, ошибок быть не должно.\n"
                + "4. Если пакетов нет, смени локацию в игре или перезайди.", 13, TEXT, false);
        hintText.setPadding(0, dp(6), 0, 0);
        hint.addView(hintText);
        col.addView(hint, lp(MATCH, WRAP, 12));

        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (web != null) web.onResume();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(ticker);
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        radarState = null;
        super.onDestroyView();
    }

    private void refresh() {
        if (!isAdded() || radarState == null) return;
        boolean radar = RadarRunner.isRunning();
        boolean cap = CaptureService.isServiceActive();
        paintDot(radarDot, radar);
        radarState.setText(radar ? "работает" : "остановлен");
        radarState.setTextColor(radar ? OK : MUTED);
        paintDot(captureDot, cap);
        captureState.setText(cap ? "активен" : "выключен");
        captureState.setTextColor(cap ? OK : MUTED);

        boolean active = radar || cap;
        if (lastActive == null || lastActive != active) {
            lastActive = active;
            mainBtn.setText(active ? "■  ОСТАНОВИТЬ" : "▶  ЗАПУСТИТЬ");
            mainBtn.setBackground(ripple(active ? shape(0x00000000, 14, RED, 2) : shape(RED, 14, 0, 0)));
        }
        boolean ov = OverlayService.running;
        overlayVal.setText(ov ? "включён" : "выключен");
        overlayVal.setTextColor(ov ? RED : MUTED);
        if (lastOverlay == null || lastOverlay != ov) {
            lastOverlay = ov;
            overlayTile.setBackground(ripple(shape(CARD, 14, ov ? RED : LINE, ov ? 2 : 1)));
        }
    }

    private void toast(String s) {
        if (isAdded()) Toast.makeText(requireContext(), s, Toast.LENGTH_LONG).show();
    }

    private void startAll() {
        final Context c = requireContext();
        if (VpnService.prepare(c) != null) {
            Intent i = new Intent(c, RadarActivity.class);
            i.putExtra("autostart", true);
            startActivity(i);
            toast("Разреши VPN в окне Android");
            return;
        }
        String err = RadarRunner.start(c);
        if (err != null) {
            toast("Радар не запустился: " + err);
            return;
        }
        Intent i = new Intent();
        i.putExtra("pcap_dump_mode", "udp_exporter");
        i.putExtra("collector_host", "127.0.0.1");
        i.putExtra("collector_port", "1234");
        i.putExtra("app_filter", ALBION_PKG);
        try {
            new CaptureHelper(c).startCapture(new CaptureSettings(c, i));
        } catch (Exception e) {
            toast("Захват не запустился: " + e);
        }
        handler.postDelayed(this::refresh, 1500);
        handler.postDelayed(() -> {
            if (isAdded() && !CaptureService.isServiceActive()) {
                Intent f = new Intent(requireContext(), RadarActivity.class);
                f.putExtra("autostart", true);
                startActivity(f);
            }
        }, 4000);
        if (previewOn) handler.postDelayed(() -> loadPage(""), 2000);
    }

    private void stopAll() {
        Context c = requireContext();
        c.stopService(new Intent(c, OverlayService.class));
        CaptureService.stopService();
        RadarRunner.stop();
        handler.postDelayed(this::refresh, 500);
        toast("Остановлено");
    }

    private void toggleOverlay() {
        Context c = requireContext();
        if (OverlayService.running) {
            c.stopService(new Intent(c, OverlayService.class));
            handler.postDelayed(this::refresh, 300);
            return;
        }
        if (!Settings.canDrawOverlays(c)) {
            toast("Разреши показ поверх других приложений, затем нажми «Оверлей» ещё раз");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + c.getPackageName())));
            return;
        }
        if (!RadarRunner.isRunning() || !CaptureService.isServiceActive()) startAll();
        ContextCompat.startForegroundService(c, new Intent(c, OverlayService.class));
        toast("Оверлей включён. Открой игру");
        handler.postDelayed(this::refresh, 500);
    }

    private void selectMainTab(int index) {
        View v = requireActivity().findViewById(R.id.tablayout);
        if (v instanceof TabLayout) {
            TabLayout.Tab t = ((TabLayout) v).getTabAt(index);
            if (t != null) t.select();
        }
    }

    private void showLog() {
        TextView t = new TextView(requireContext());
        t.setText(RadarRunner.readLog(requireContext()));
        t.setTextIsSelectable(true);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        ScrollView sv = new ScrollView(requireContext());
        sv.addView(t);
        new AlertDialog.Builder(requireContext()).setTitle("Лог радара").setView(sv)
                .setPositiveButton("OK", null).show();
    }

    private void togglePreview() {
        if (previewOn) {
            previewOn = false;
            previewBox.setVisibility(View.GONE);
            previewToggle.setText("Показать");
            if (web != null) web.setVisibility(View.GONE);
        } else {
            showPage("");
        }
    }

    private void showPage(String path) {
        previewOn = true;
        previewBox.setVisibility(View.VISIBLE);
        previewToggle.setText("Скрыть");
        if (!RadarRunner.isRunning()) toast("Радар не запущен: сначала нажми «Запустить»");
        loadPage(path);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadPage(String path) {
        currentUrl = RADAR_URL + path;
        retries = 0;
        if (web == null) {
            web = new WebView(requireContext());
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            web.setBackgroundColor(BG);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                    if (r.isForMainFrame() && retries++ < 20)
                        handler.postDelayed(() -> v.loadUrl(currentUrl), 1000);
                }
            });
            previewBox.addView(web, lp(MATCH, dp(420), 10));
        }
        web.setVisibility(View.VISIBLE);
        web.loadUrl(currentUrl);
    }
}
