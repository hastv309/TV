package com.hussam.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
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

/** At prayer time: a banner slides down over whatever is on screen, a soft chime, then a voice says the prayer. */
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
        h.postDelayed(() -> speak(c, "حان الآن موعد صلاة " + ar), 1400);
        h.postDelayed(pr::finish, 9000);
    }

    static void show(Context c, String ar, String en, String time) {
        playChime(c);
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
                }).start(), 8000);
    }

    static void playChime(Context c) {
        try {
            MediaPlayer mp = MediaPlayer.create(c, R.raw.chime, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0);
            if (mp == null) return;
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } catch (Exception ignored) { }
    }

    static void speak(Context c, String text) {
        try {
            if (tts != null) { say(text); return; }
            tts = new TextToSpeech(c, status -> {
                if (status == TextToSpeech.SUCCESS) say(text); else tts = null;
            });
        } catch (Exception ignored) { }
    }

    private static void say(String text) {
        int r = tts.setLanguage(new Locale("ar", "SA"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = tts.setLanguage(new Locale("ar"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return;
        tts.setSpeechRate(0.9f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer");
    }
}
