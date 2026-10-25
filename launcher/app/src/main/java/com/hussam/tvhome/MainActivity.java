package com.hussam.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
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
    final List<String> watchIntents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                // Stay on our own page; never navigate the home screen elsewhere.
                return !(HOME_HOST.equals(u.getHost()) || "file".equals(u.getScheme()));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !usingFallback) {
                    usingFallback = true;
                    view.loadUrl(FALLBACK_URL);
                }
            }
        });
        setContentView(web);
        web.setFocusable(true);
        web.requestFocus();
        web.loadUrl(HOME_URL + "?d=" + (System.currentTimeMillis() / 3600000L));

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("com.android.providers.tv.permission.READ_EPG_DATA") != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{"com.android.providers.tv.permission.READ_EPG_DATA"}, 1);
            } catch (Exception ignored) {
            }
        }
    }

    void js(String code) {
        if (web != null) web.post(() -> web.evaluateJavascript(code, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        appCache.clear();
        js("window.onResumeHome&&onResumeHome()");
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

        /** minutes > 0: schedule; 0: cancel; < 0: turn off now. Returns false when sleep is not set up. */
        @JavascriptInterface
        public boolean sleepIn(int minutes) {
            Context ctx = MainActivity.this;
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean admin = dpm != null && dpm.isAdminActive(new ComponentName(ctx, SleepAdmin.class));
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 7, new Intent(ctx, SleepReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.cancel(pi);
            if (minutes == 0) return true;
            if (!admin) return false;
            if (minutes < 0) return SleepReceiver.lockNow(ctx);
            long at = System.currentTimeMillis() + minutes * 60000L;
            if (am != null) am.set(AlarmManager.RTC_WAKEUP, at, pi);
            return true;
        }

        @JavascriptInterface
        public boolean sleepReady() {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isAdminActive(new ComponentName(MainActivity.this, SleepAdmin.class));
        }

        @JavascriptInterface
        public String watchNext() {
            JSONObject res = new JSONObject();
            JSONArray items = new JSONArray();
            synchronized (watchIntents) {
                watchIntents.clear();
                Cursor c = null;
                try {
                    Uri uri = Uri.parse("content://android.media.tv/watch_next_program");
                    try {
                        c = getContentResolver().query(uri, null, null, null, "last_engagement_time_utc_millis DESC");
                    } catch (RuntimeException bad) {
                        c = getContentResolver().query(uri, null, null, null, null);
                    }
                    if (c != null) {
                        while (c.moveToNext() && items.length() < 12) {
                            String title = str(c, "title");
                            String intentUri = str(c, "intent_uri");
                            if (title == null || title.isEmpty() || intentUri == null) continue;
                            JSONObject o = new JSONObject();
                            o.put("title", title);
                            String ep = str(c, "episode_title");
                            String season = str(c, "season_display_number");
                            String num = str(c, "episode_display_number");
                            StringBuilder sub = new StringBuilder();
                            if (season != null && !season.isEmpty()) sub.append("S").append(season);
                            if (num != null && !num.isEmpty()) sub.append(sub.length() > 0 ? " · " : "").append("E").append(num);
                            if (ep != null && !ep.isEmpty()) sub.append(sub.length() > 0 ? " · " : "").append(ep);
                            o.put("sub", sub.toString());
                            o.put("pkg", str(c, "package_name"));
                            long pos = lng(c, "last_playback_position_millis");
                            long dur = lng(c, "duration_millis");
                            if (dur > 0 && pos >= 0) {
                                o.put("progress", Math.min(1.0, pos / (double) dur));
                                o.put("left", Math.max(1, (dur - pos) / 60000L));
                            }
                            String img = str(c, "thumbnail_uri");
                            if (img == null || img.isEmpty()) img = str(c, "poster_art_uri");
                            if (img != null && !img.isEmpty()) {
                                if (img.startsWith("http")) o.put("img", img);
                                else {
                                    Bitmap b = loadImage(img);
                                    if (b != null) o.put("img", bitmapToDataUrl(b, true));
                                }
                            }
                            watchIntents.add(intentUri);
                            items.put(o);
                        }
                    }
                    res.put("ok", true);
                } catch (SecurityException se) {
                    try { res.put("ok", false); res.put("error", "permission"); } catch (Exception ignored) { }
                } catch (Exception e) {
                    try { res.put("ok", false); res.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
                } finally {
                    if (c != null) c.close();
                }
            }
            try { res.put("items", items); } catch (Exception ignored) { }
            return res.toString();
        }

        @JavascriptInterface
        public boolean openWatchNext(int index) {
            String uri;
            synchronized (watchIntents) {
                if (index < 0 || index >= watchIntents.size()) return false;
                uri = watchIntents.get(index);
            }
            try {
                Intent i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                return start(i);
            } catch (Exception e) {
                return false;
            }
        }
    }

    static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return null;
        return c.getString(i);
    }

    static long lng(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return -1;
        return c.getLong(i);
    }
}
