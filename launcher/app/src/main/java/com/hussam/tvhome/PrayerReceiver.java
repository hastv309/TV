package com.hussam.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** At prayer time: a banner slides down over whatever is on screen, then a calm voice says the prayer (no music). */
public class PrayerReceiver extends BroadcastReceiver {
    static TextToSpeech tts;

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        long at = intent.getLongExtra("at", 0);
        // Skip stale alerts (the TV was asleep at prayer time) and alerts while the screen is off.
        if (Math.abs(System.currentTimeMillis() - at) > 3 * 60_000L) return;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;

        String ar = intent.getStringExtra("ar");
        String en = intent.getStringExtra("en");
        String time = intent.getStringExtra("time");
        final PendingResult pr = goAsync();
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> show(c, ar, en, time));
        h.postDelayed(() -> speak(c, "حان الآن موعد صلاة " + ar), 700);
        h.postDelayed(pr::finish, 22000);
    }

    static void show(Context c, String ar, String en, String time) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        float dp = c.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding((int) (36 * dp), (int) (16 * dp), (int) (36 * dp), (int) (16 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 18, 14, 22));
        bg.setCornerRadius(28 * dp);
        bg.setStroke((int) (1.5f * dp), Color.argb(150, 255, 210, 154));
        card.setBackground(bg);

        TextView t1 = new TextView(c);
        t1.setText("حان الآن موعد صلاة " + ar);
        t1.setTextColor(Color.rgb(251, 247, 240));
        t1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        TextView t2 = new TextView(c);
        t2.setText(en.toUpperCase(Locale.US) + "  ·  " + time);
        t2.setTextColor(Color.rgb(255, 210, 154));
        t2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t2.setLetterSpacing(0.12f);
        card.addView(t1);
        card.addView(t2);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (28 * dp);
        try {
            wm.addView(card, lp);
        } catch (Exception e) {
            return;
        }
        card.setAlpha(0f);
        card.setTranslationY(-120 * dp);
        card.animate().alpha(1f).translationY(0).setDuration(450).start();
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                card.animate().alpha(0f).translationY(-120 * dp).setDuration(450).withEndAction(() -> {
                    try { wm.removeView(card); } catch (Exception ignored) { }
                }).start(), 20000);
    }

    static Context appCtx;

    static void speak(Context c, String text) {
        appCtx = c.getApplicationContext();
        withTts(c, () -> say(text));
    }

    /** Runs r once the speech engine is ready. */
    static void withTts(Context c, Runnable r) {
        try {
            appCtx = c.getApplicationContext();
            if (tts != null) { r.run(); return; }
            tts = new TextToSpeech(appCtx, status -> {
                if (status == TextToSpeech.SUCCESS) r.run(); else tts = null;
            });
        } catch (Exception ignored) { }
    }

    /** Arabic voices installed on the TV, sorted by name. */
    static java.util.List<android.speech.tts.Voice> arabicVoices() {
        java.util.List<android.speech.tts.Voice> out = new java.util.ArrayList<>();
        try {
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v.getLocale() != null && "ar".equals(v.getLocale().getLanguage())
                        && (v.getFeatures() == null || !v.getFeatures().contains("notInstalled"))) out.add(v);
            }
        } catch (Exception ignored) { }
        java.util.Collections.sort(out, (a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    /** Picks the saved voice, else one that looks male, else the engine's Arabic default. */
    private static void applyVoice() {
        java.util.List<android.speech.tts.Voice> vs = arabicVoices();
        String saved = appCtx == null ? null : appCtx.getSharedPreferences("home", Context.MODE_PRIVATE).getString("voice", null);
        android.speech.tts.Voice pick = null;
        for (android.speech.tts.Voice v : vs) if (v.getName().equals(saved)) pick = v;
        if (pick == null) for (android.speech.tts.Voice v : vs) {
            String n = v.getName().toLowerCase(Locale.US);
            if (n.contains("male") && !n.contains("female")) { pick = v; break; }
        }
        if (pick != null) tts.setVoice(pick);
    }

    private static void say(String text) {
        int r = tts.setLanguage(new Locale("ar", "SA"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = tts.setLanguage(new Locale("ar"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return;
        applyVoice();
        tts.setSpeechRate(0.85f);
        tts.setPitch(0.9f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer");
    }

    /** Switches to the next Arabic voice, says a sample, and returns "n/total". */
    static void nextVoice(Context c, java.util.function.Consumer<String> done) {
        withTts(c, () -> {
            java.util.List<android.speech.tts.Voice> vs = arabicVoices();
            if (vs.isEmpty()) { done.accept("0/0"); return; }
            android.content.SharedPreferences p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
            String saved = p.getString("voice", null);
            int i = -1;
            for (int k = 0; k < vs.size(); k++) if (vs.get(k).getName().equals(saved)) i = k;
            int n = (i + 1) % vs.size();
            p.edit().putString("voice", vs.get(n).getName()).apply();
            say("حان الآن موعد صلاة المغرب");
            done.accept((n + 1) + "/" + vs.size());
        });
    }
}
