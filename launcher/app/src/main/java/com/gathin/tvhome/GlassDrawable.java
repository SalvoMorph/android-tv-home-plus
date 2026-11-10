package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A view's background drawn as {@link LiquidGlass}, fully rounded (a pill or a circle). */
final class GlassDrawable extends Drawable {

    private final LiquidGlass glass;
    private final View host;
    private final int[] where = new int[2];
    private final RectF rect = new RectF();

    GlassDrawable(LiquidGlass glass, View host) {
        this.glass = glass;
        this.host = host;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        View root = host.getRootView();
        if (b.isEmpty() || root == null || root.getWidth() == 0) return;
        host.getLocationInWindow(where);
        rect.set(b);
        glass.draw(canvas, rect, Math.min(b.width(), b.height()) / 2f, where[0], where[1], root.getWidth(), root.getHeight());
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
