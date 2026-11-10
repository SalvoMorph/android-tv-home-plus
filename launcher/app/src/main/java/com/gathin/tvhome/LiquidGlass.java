package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * A drawn "liquid glass" panel for the big-clock look: what a frosted glass would show of the wallpaper behind it
 * (a tiny blurred copy of the picture, mapped to the panel's place on screen), lifted in brightness and colour,
 * a white tint, a soft highlight across the top and a bright rim that fades along the diagonal.
 *
 * It costs one small-bitmap shader fill and three gradient fills per panel, with no blur, no offscreen layer and
 * no clip, so it is as cheap as the plain translucent panels it replaces. It only suits a backdrop that stays still
 * (the drawn scene does), because the blurred copy is fixed to the screen.
 */
final class LiquidGlass {

    private final float density;
    private final float bitmapW, bitmapH;
    private final BitmapShader backdrop;
    private final Matrix matrix = new Matrix();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cachedL = Float.NaN, cachedT, cachedR, cachedB;
    private int shineTop = 0x52FFFFFF;

    /** @param blur a small, already blurred copy of the whole backdrop; it is stretched over the whole window. */
    LiquidGlass(Bitmap blur, float density) {
        this(blur, density, false);
    }

    /** @param dark glass for a pale picture: a navy tint instead of a white one, and no lift in brightness. */
    LiquidGlass(Bitmap blur, float density, boolean dark) {
        this.density = density;
        this.bitmapW = blur.getWidth();
        this.bitmapH = blur.getHeight();
        backdrop = new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        fill.setShader(backdrop);
        fill.setFilterBitmap(true);
        // Richer and a touch lighter than the picture, as glass over a blur looks.
        ColorMatrix color = new ColorMatrix();
        color.setSaturation(1.15f);
        float lift = dark ? 1f : 1.08f;
        float offset = dark ? -14f : 10f;
        color.postConcat(new ColorMatrix(new float[]{
                lift, 0, 0, 0, offset,
                0, lift, 0, 0, offset,
                0, 0, lift, 0, offset,
                0, 0, 0, 1, 0}));
        fill.setColorFilter(new ColorMatrixColorFilter(color));
        // Not fully opaque: the sharp picture shows faintly through the blurred copy, which is what makes it glass.
        fill.setAlpha(dark ? 150 : 170);
        tint.setColor(dark ? 0x4D223C6C : 0x24FFFFFF);
        shineTop = dark ? 0x38FFFFFF : 0x52FFFFFF;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1.5f, 1.4f * density));
    }

    /**
     * Draws the panel into {@code r} (canvas coordinates).
     *
     * @param originX the canvas origin's place in the window, in px, so the blurred copy lines up with the picture
     * @param winW    the window's size in px
     */
    void draw(Canvas c, RectF r, float radius, float originX, float originY, float winW, float winH) {
        matrix.setScale(winW / bitmapW, winH / bitmapH);
        matrix.postTranslate(-originX, -originY);
        backdrop.setLocalMatrix(matrix);
        if (r.left != cachedL || r.top != cachedT || r.right != cachedR || r.bottom != cachedB) {
            float h = r.height();
            shine.setShader(new LinearGradient(0, r.top, 0, r.top + h * 0.55f, shineTop, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0xE6FFFFFF, 0x33FFFFFF, 0x26FFFFFF, 0x99FFFFFF},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            cachedL = r.left;
            cachedT = r.top;
            cachedR = r.right;
            cachedB = r.bottom;
        }
        c.drawRoundRect(r, radius, radius, fill);
        c.drawRoundRect(r, radius, radius, tint);
        c.drawRoundRect(r, radius, radius, shine);
        float half = rim.getStrokeWidth() / 2f;
        RectF edge = new RectF(r.left + half, r.top + half, r.right - half, r.bottom - half);
        c.drawRoundRect(edge, Math.max(0f, radius - half), Math.max(0f, radius - half), rim);
    }
}
