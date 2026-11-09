package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The weather glyph, drawn with a few shapes on a 48 x 48 grid: no bitmaps, no animation, no allocation in
 * onDraw. It redraws only when the condition changes, so it costs the TV nothing while Home sits idle.
 */
public final class WeatherView extends View {
    private static final int SUN = 0xFFFFC93C;
    private static final int MOON = 0xFFE7EEF9;
    private static final int CLOUD = 0xFFEAF1F8;
    private static final int CLOUD_DARK = 0xFF9AA9BA;
    private static final int DROP = 0xFF5AB0FF;
    private static final int BOLT = 0xFFFFD84D;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path cut = new Path();
    private final RectF rect = new RectF();
    private Weather.Kind kind = Weather.Kind.CLEAR;
    private boolean day = true;

    public WeatherView(Context context) {
        super(context);
    }

    public WeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void set(Weather.Kind kind, boolean day) {
        if (this.kind == kind && this.day == day) return;
        this.kind = kind;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 48f, size / 48f);
        switch (kind) {
            case CLEAR:
                sunOrMoon(canvas, 24, 24, 9);
                break;
            case PARTLY:
                sunOrMoon(canvas, 31, 17, 8);
                cloud(canvas, 0, 4, CLOUD);
                break;
            case CLOUDY:
                cloud(canvas, -3, -2, CLOUD_DARK);
                cloud(canvas, 3, 4, CLOUD);
                break;
            case FOG:
                cloud(canvas, 0, -6, CLOUD);
                lines(canvas, CLOUD_DARK, 10, 34, 38, 34, 14, 40, 34, 40);
                break;
            case RAIN:
                cloud(canvas, 0, -6, CLOUD_DARK);
                lines(canvas, DROP, 15, 32, 12, 41, 24, 32, 21, 41, 33, 32, 30, 41);
                break;
            case SNOW:
                cloud(canvas, 0, -6, CLOUD);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(15, 36, 2.4f, paint);
                canvas.drawCircle(24, 41, 2.4f, paint);
                canvas.drawCircle(33, 36, 2.4f, paint);
                break;
            case STORM:
                cloud(canvas, 0, -6, CLOUD_DARK);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(BOLT);
                path.reset();
                path.moveTo(26, 27);
                path.lineTo(18, 38);
                path.lineTo(23, 38);
                path.lineTo(20, 46);
                path.lineTo(31, 33);
                path.lineTo(25, 33);
                path.lineTo(29, 27);
                path.close();
                canvas.drawPath(path, paint);
                break;
        }
        canvas.restore();
    }

    private void sunOrMoon(Canvas c, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.FILL);
        if (day) {
            paint.setColor(SUN);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2.6f);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(cx + cos * (r + 4), cy + sin * (r + 4), cx + cos * (r + 8), cy + sin * (r + 8), paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else {
            path.reset();
            path.addCircle(cx, cy, r + 1, Path.Direction.CW);
            cut.reset();
            cut.addCircle(cx + 6, cy - 4, r, Path.Direction.CW);
            path.op(cut, Path.Op.DIFFERENCE);
            paint.setColor(MOON);
            c.drawPath(path, paint);
        }
    }

    private void cloud(Canvas c, float dx, float dy, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(15 + dx, 29 + dy, 7.5f, paint);
        c.drawCircle(25 + dx, 23 + dy, 10.5f, paint);
        c.drawCircle(35 + dx, 29 + dy, 6.5f, paint);
        rect.set(15 + dx, 28 + dy, 35 + dx, 36.5f + dy);
        c.drawRoundRect(rect, 3f, 3f, paint);
    }

    /** Round-capped strokes; each four numbers are x1, y1, x2, y2. */
    private void lines(Canvas c, int color, float... p) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f);
        paint.setColor(color);
        for (int i = 0; i + 3 < p.length; i += 4) c.drawLine(p[i], p[i + 1], p[i + 2], p[i + 3], paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
