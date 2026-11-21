package com.hussam.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Prayer card background: dark rounded card, hexagon strip on top, faint crescent and star, mosque skyline at the bottom. */
class PrayerCardBg extends Drawable {
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hex = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky = new Paint(Paint.ANTI_ALIAS_FLAG);

    PrayerCardBg(float dp) {
        this.dp = dp;
        fill.setColor(Color.argb(238, 18, 14, 22));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(Color.argb(150, 255, 210, 154));
        hex.setStyle(Paint.Style.STROKE);
        hex.setStrokeWidth(1f * dp);
        hex.setColor(Color.argb(105, 255, 210, 154));
        moon.setColor(Color.argb(26, 255, 210, 154));
        sky.setColor(Color.argb(72, 255, 210, 154));
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float r = 22 * dp;
        RectF rf = new RectF(b);
        Path clip = new Path();
        clip.addRoundRect(rf, r, r, Path.Direction.CW);
        c.drawPath(clip, fill);
        c.save();
        c.clipPath(clip);

        // Big faint crescent + star, top-left corner.
        float cx = b.left + 30 * dp, cy = b.top + 40 * dp, R = 70 * dp;
        Path m = new Path();
        m.addCircle(cx, cy, R, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + R * 0.45f, cy - R * 0.25f, R * 0.82f, Path.Direction.CW);
        m.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(m, moon);
        c.drawPath(star(cx + R * 0.55f, cy - R * 0.35f, 17 * dp), moon);

        // Hexagon strip along the top edge.
        float w = 28 * dp, h = 8 * dp;
        Path z = new Path();
        for (float x = b.left - w; x < b.right + w; x += w) {
            z.moveTo(x, b.top + h);
            z.lineTo(x + w / 2, b.top);
            z.lineTo(x + w, b.top + h);
            z.moveTo(x + w, b.top + h);
            z.lineTo(x + w, b.top + h * 2.4f);
        }
        c.drawPath(z, hex);

        // Mosque skyline at the bottom centre (dome, hall, two minarets).
        float sw = Math.min(b.width() - 60 * dp, 420 * dp), s = sw / 400f;
        float x0 = b.centerX() - sw / 2, yb = b.bottom;
        Path p = new Path();
        p.addRect(x0, yb - 8 * s, x0 + 400 * s, yb, Path.Direction.CW);
        p.addRect(x0 + 90 * s, yb - 24 * s, x0 + 310 * s, yb, Path.Direction.CW);
        p.addArc(new RectF(x0 + 150 * s, yb - 50 * s, x0 + 250 * s, yb - 10 * s), 180, 180);
        p.addRect(x0 + 150 * s, yb - 30 * s, x0 + 250 * s, yb, Path.Direction.CW);
        p.addCircle(x0 + 200 * s, yb - 52 * s, 3 * s, Path.Direction.CW);
        for (float mx : new float[]{40, 354}) {
            p.addRect(x0 + mx * s, yb - 50 * s, x0 + (mx + 6) * s, yb, Path.Direction.CW);
            Path tip = new Path();
            tip.moveTo(x0 + (mx + 3) * s, yb - 60 * s);
            tip.lineTo(x0 + (mx - 1) * s, yb - 48 * s);
            tip.lineTo(x0 + (mx + 7) * s, yb - 48 * s);
            tip.close();
            p.addPath(tip);
        }
        c.drawPath(p, sky);
        c.restore();

        RectF in = new RectF(rf);
        in.inset(stroke.getStrokeWidth() / 2, stroke.getStrokeWidth() / 2);
        c.drawRoundRect(in, r, r, stroke);
    }

    private static Path star(float cx, float cy, float r) {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) (Math.cos(a) * rr), y = cy - (float) (Math.sin(a) * rr);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
