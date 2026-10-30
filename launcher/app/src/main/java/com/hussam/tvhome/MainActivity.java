package com.hussam.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home screen: a full-screen WebView showing home.html from GitHub Pages (bundled copy as offline fallback),
 * plus a small bridge ("Tv") that lists and opens apps, opens settings, starts the screensaver,
 * runs the sleep timer and reads the system "Watch Next" (continue watching) list.
 */
public class MainActivity extends Activity {

    static final String HOME_URL = "https://hastv309.github.io/TV/home.html";
    static final String FALLBACK_URL = "file:///android_asset/home.html";
    static final String HOME_HOST = "hastv309.github.io";

    WebView web;
    boolean usingFallback = false;
    final Map<String, JSONObject> appCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        client = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                // Stay on our own page; never navigate the home screen elsewhere.
                return !(HOME_HOST.equals(u.getHost()) || "file".equals(u.getScheme()));
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Renderer crashed or was killed for memory: start a fresh WebView right away.
                new android.os.Handler(android.os.Looper.getMainLooper()).post(MainActivity.this::buildWebView);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !usingFallback) {
                    usingFallback = true;
                    view.loadUrl(FALLBACK_URL);
                }
            }
        };
        buildWebView();
    }

    WebViewClient client;

    void buildWebView() {
        if (web != null) {
            try { ((android.view.ViewGroup) web.getParent()).removeView(web); web.destroy(); } catch (Exception ignored) { }
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(13, 10, 16));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "Tv");
        web.setWebViewClient(client);
        setContentView(web);
        web.setFocusable(true);
        web.requestFocus();
        usingFallback = false;
        web.loadUrl(HOME_URL + "?d=" + (System.currentTimeMillis() / 3600000L));
    }

    void js(String code) {
        if (web != null) web.post(() -> web.evaluateJavascript(code, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        js("window.onResumeHome&&onResumeHome()");
        // give the system a few seconds to bind the reader, then wake it if it is still off
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                new Thread(() -> Adb.reviveWatchService(getApplicationContext())).start(), 6000);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on the home screen.
        js("window.onHome&&onHome()");
    }

    @Override
    public void onBackPressed() {
        // The home screen never closes itself.
        js("window.onBack&&onBack()");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        js("window.onResumeHome&&onResumeHome()");
    }

    /* ------------------------------------------------------------------ */

    static String bitmapToDataUrl(Bitmap b, boolean jpeg) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, jpeg ? 82 : 100, out);
        return (jpeg ? "data:image/jpeg;base64," : "data:image/png;base64,")
                + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Draw a banner (or a centred icon on a dark card) into a 320x180 bitmap. */
    static Bitmap renderTile(Drawable d, boolean isBanner) {
        int w = 320, h = 180;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (isBanner) {
            d.setBounds(0, 0, w, h);
        } else {
            c.drawColor(Color.rgb(34, 30, 40));
            int s = 112;
            d.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        }
        d.draw(c);
        return b;
    }

    /** Pick a strong, not-too-dark colour from the tile for the background glow. */
    static String accentColor(Bitmap src) {
        Bitmap small = Bitmap.createScaledBitmap(src, 32, 18, true);
        double best = -1;
        int pick = Color.rgb(90, 26, 42);
        float[] hsv = new float[3];
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                int p = small.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                double score = hsv[1] * hsv[2];
                if (hsv[2] < 0.18) continue;
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
                if (score > best) { best = score; pick = p; }
            }
        }
        if (best < 0.12 && n > 0) pick = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        Color.colorToHSV(pick, hsv);
        if (hsv[2] > 0.85f) hsv[2] = 0.85f;
        if (hsv[2] < 0.35f) hsv[2] = 0.35f;
        int c = Color.HSVToColor(hsv);
        return String.format("#%02x%02x%02x", Color.red(c), Color.green(c), Color.blue(c));
    }

    Bitmap loadImage(String uri) {
        try {
            Uri u = Uri.parse(uri);
            InputStream in = getContentResolver().openInputStream(u);
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            if (b == null) return null;
            int w = 480, h = Math.max(1, b.getHeight() * w / Math.max(1, b.getWidth()));
            return Bitmap.createScaledBitmap(b, w, h, true);
        } catch (Exception e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ */

    class Bridge {

        /** Force-stops every launchable app (through the TV's own ADB) and reports the memory freed. */
        @JavascriptInterface
        public void clean() {
            new Thread(() -> {
                long freed = 0;
                boolean full = false;
                try {
                    android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    android.app.ActivityManager.MemoryInfo before = new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(before);
                    if (!Adb.ready(MainActivity.this)) js("window.toast&&toast('On the TV prompt, tick Always allow and press Allow')");
                    full = Adb.cleanAll(MainActivity.this);
                    if (full) Adb.grantExtras(MainActivity.this);
                    Thread.sleep(1200);
                    android.app.ActivityManager.MemoryInfo after = new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(after);
                    freed = Math.max(0, (after.availMem - before.availMem) / (1024 * 1024));
                } catch (Exception ignored) {
                }
                js("window.onCleaned&&onCleaned(" + freed + "," + full + ")");
            }).start();
        }

        /** Shows the prayer banner and voice after a delay, so you can open another app to see it on top. */
        @JavascriptInterface
        public void testPrayer(int delaySec) {
            Context c = getApplicationContext();
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> PrayerReceiver.show(c, "المغرب", "Maghrib", "5:20 PM"), delaySec * 1000L);
            h.postDelayed(() -> PrayerReceiver.speak(c, "حان الآن موعد صلاة المغرب"), delaySec * 1000L + 700);
        }

        /** Cycles the Arabic voice used for prayer alerts; the page gets onVoice("n/total"). */
        @JavascriptInterface
        public void nextVoice() {
            PrayerReceiver.nextVoice(getApplicationContext(), r -> js("window.onVoice&&onVoice('" + r + "')"));
        }

        @JavascriptInterface
        public void clearWatch() {
            WatchStore.clear(MainActivity.this);
        }

        @JavascriptInterface
        public String watchList() {
            return WatchStore.list(MainActivity.this);
        }

        /** Opens the app a watched item came from. */
        @JavascriptInterface
        public boolean openWatch(String pkg, String vid) {
            try {
                Intent it = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (it == null) it = getPackageManager().getLaunchIntentForPackage(pkg);
                if (it == null) return false;
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Sets one alarm per upcoming prayer: [{ar, en, at, time}, ...]. */
        @JavascriptInterface
        public void schedulePrayers(String json) {
            try {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < 10; i++) {
                    Intent it = new Intent(MainActivity.this, PrayerReceiver.class);
                    if (i < a.length()) {
                        JSONObject o = a.getJSONObject(i);
                        it.putExtra("ar", o.getString("ar")).putExtra("en", o.getString("en"))
                          .putExtra("time", o.getString("time")).putExtra("at", o.getLong("at"));
                    }
                    PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, 100 + i, it,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    if (i >= a.length()) { am.cancel(pi); continue; }
                    long at = a.getJSONObject(i).getLong("at");
                    boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                    if (exact) am.setExact(AlarmManager.RTC, at, pi);
                    else am.setWindow(AlarmManager.RTC, at, 60_000L, pi);
                }
                // Banner permission and exact alarms are granted once through the TV's own ADB.
                if (Adb.ready(MainActivity.this) && (!Adb.extrasOn(MainActivity.this)
                        || (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()))) {
                    new Thread(() -> Adb.grantExtras(MainActivity.this)).start();
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String apps() {
            JSONArray out = new JSONArray();
            try {
                PackageManager pm = getPackageManager();
                Set<String> seen = new HashSet<>();
                List<ResolveInfo> all = new ArrayList<>(pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
                for (ResolveInfo ri : all) {
                    ActivityInfo ai = ri.activityInfo;
                    String pkg = ai.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    long updated = 0;
                    try {
                        PackageInfo pi = pm.getPackageInfo(pkg, 0);
                        updated = pi.lastUpdateTime;
                    } catch (Exception ignored) {
                    }
                    String key = pkg + "@" + updated;
                    JSONObject o = appCache.get(key);
                    if (o == null) {
                        o = new JSONObject();
                        o.put("pkg", pkg);
                        o.put("label", String.valueOf(ri.loadLabel(pm)));
                        Drawable d = ai.loadBanner(pm);
                        if (d == null) d = ai.applicationInfo.loadBanner(pm);
                        boolean isBanner = d != null;
                        if (d == null) d = ri.loadIcon(pm);
                        Bitmap tile = renderTile(d, isBanner);
                        o.put("banner", bitmapToDataUrl(tile, false));
                        o.put("color", accentColor(tile));
                        appCache.put(key, o);
                    }
                    out.put(o);
                }
            } catch (Exception ignored) {
            }
            return out.toString();
        }

        @JavascriptInterface
        public boolean launch(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                if (i == null) return false;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final Intent go = i;
                runOnUiThread(() -> {
                    try { startActivity(go); } catch (Exception ignored) { }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        boolean start(Intent i) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean openSettings() {
            return start(new Intent(Settings.ACTION_SETTINGS));
        }

        @JavascriptInterface
        public boolean openWifi() {
            if (start(new Intent(Settings.ACTION_WIFI_SETTINGS))) return true;
            return start(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }

        @JavascriptInterface
        public boolean screensaver() {
            Intent dream = new Intent(Intent.ACTION_MAIN);
            dream.setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"));
            if (start(dream)) return true;
            return start(new Intent(Settings.ACTION_DREAM_SETTINGS));
        }
    }
}
