package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The backdrop of the "big clock" look (config clock_look), drawn like a phone lock screen: a dusk photograph
 * (Ama Dablam, CC BY-SA 4.0, see themes/clock-look/CREDITS.md), a huge glass clock, and the mountains drawn again
 * on top of the clock's lower part so the digits sit behind them.
 *
 * The clock is glass: what a frosted pane would show of the picture (a tiny blurred copy), lifted in colour, a
 * white tint, and a bright rim. It is drawn here, not by a TextClock, because only a layer between the photograph
 * and the mountains can be hidden behind them. The mountains in front are a cached bitmap of just the clock's
 * region (the picture clipped to its skyline), made once.
 *
 * Nothing here animates: the view redraws once a minute for the time, and otherwise is replayed from its display list.
 * It hands out a tiny blurred copy of the picture for the glass panels elsewhere (see LiquidGlass).
 */
public class LockScreenView extends View {

    private static final int BLUR_W = 64;
    private static final int BLUR_H = 36;
    /** The clock: where its top sits, how tall the digits are and how much they are squeezed, as fractions of the view. */
    private static final float CLOCK_TOP = 0.215f;
    private static final float CLOCK_SIZE = 0.50f;
    private static final float CLOCK_SQUEEZE = 0.74f;
    /** The region (fractions of the view) that holds the clock and gets a copy of the mountains in front of it. */
    private static final float FRONT_L = 0.20f, FRONT_R = 0.80f, FRONT_T = 0.16f, FRONT_B = 0.66f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Paint glassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassTint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dest = new Rect();
    private final Path clock = new Path();
    private final RectF clockBounds = new RectF();
    private final Matrix matrix = new Matrix();

    private Bitmap photo;
    private Bitmap blur;
    private BitmapShader blurShader;
    private Bitmap front;
    private int frontLeft, frontTop;
    private int builtW, builtH;
    private int frontW, frontH;
    private String time = "";
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshTime();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000L - now % 60_000L + 50L);
        }
    };

    public LockScreenView(Context context) {
        super(context);
        init();
    }

    public LockScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ColorMatrix color = new ColorMatrix();
        color.setSaturation(1.3f);
        color.postConcat(new ColorMatrix(new float[]{
                1.1f, 0, 0, 0, 34,
                0, 1.1f, 0, 0, 34,
                0, 0, 1.1f, 0, 34,
                0, 0, 0, 1, 0}));
        glassFill.setColorFilter(new ColorMatrixColorFilter(color));
        glassFill.setAlpha(215);
        glassRim.setStyle(Paint.Style.STROKE);
        glassRim.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Full-screen: without this every redraw of Home goes through an offscreen layer. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private MountainScenes.Scene scene = MountainScenes.MOUNTAIN;

    /** Chooses the picture (before the first draw). A scene whose picture is not in this build falls back to Mountain. */
    void setScene(MountainScenes.Scene chosen) {
        if (chosen == scene) return;
        scene = chosen;
        invalidatePicture();
        invalidate();
    }

    private void invalidatePicture() {
        if (photo != null) photo.recycle();
        photo = null;
        if (front != null) front.recycle();
        front = null;
        blur = null;
        blurShader = null;
        builtW = 0;
        frontW = 0;
    }

    private void loadPhoto() {
        if (photo != null) return;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        int id = getResources().getIdentifier(scene.drawable, "drawable", getContext().getPackageName());
        if (id == 0) {
            scene = MountainScenes.MOUNTAIN;
            id = R.drawable.scenic;
        }
        photo = BitmapFactory.decodeResource(getResources(), id, options);
    }

    /** A tiny blurred copy of the picture for LiquidGlass and the clock, or null if the picture cannot be decoded. */
    Bitmap blur() {
        if (blur != null) return blur;
        loadPhoto();
        if (photo == null) return null;
        // Shrink to a few dozen pixels (a first blur), blur those with a box filter, then grow back in steps with
        // filtering: stretched in one go a tiny bitmap shows its pixels as soft blocks.
        Bitmap small = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(photo, 192, 108, true), BLUR_W, BLUR_H, true);
        int[] px = new int[BLUR_W * BLUR_H];
        small.getPixels(px, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H);
        for (int pass = 0; pass < 3; pass++) boxBlur(px, BLUR_W, BLUR_H, 2);
        small.setPixels(px, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H);
        Bitmap big = small;
        for (int step = 0; step < 2; step++) {
            Bitmap next = Bitmap.createScaledBitmap(big, big.getWidth() * 2, big.getHeight() * 2, true);
            if (big != next && big != small) big.recycle();
            big = next;
        }
        if (big != small) small.recycle();
        blur = big;
        return blur;
    }

    /** One box-blur pass of radius r over ARGB pixels (horizontal, then vertical), edges clamped. */
    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        int span = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0, rr = 0, g = 0, b = 0;
                for (int k = -r; k <= r; k++) {
                    int c = px[y * w + Math.min(w - 1, Math.max(0, x + k))];
                    a += c >>> 24;
                    rr += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                tmp[y * w + x] = ((a / span) << 24) | ((rr / span) << 16) | ((g / span) << 8) | (b / span);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0, rr = 0, g = 0, b = 0;
                for (int k = -r; k <= r; k++) {
                    int c = tmp[Math.min(h - 1, Math.max(0, y + k)) * w + x];
                    a += c >>> 24;
                    rr += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                px[y * w + x] = ((a / span) << 24) | ((rr / span) << 16) | ((g / span) << 8) | (b / span);
            }
        }
    }

    // ---- the time ---------------------------------------------------------------------------------

    private void refreshTime() {
        boolean is24 = DateFormat.is24HourFormat(getContext());
        String now = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date());
        if (now.equals(time)) return;
        time = now;
        builtW = 0;   // the clock's outline is rebuilt on the next draw
        invalidate();
    }

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        handler.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) startTicking();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTicking();
        if (photo != null) {
            photo.recycle();
            photo = null;
        }
        if (front != null) {
            front.recycle();
            front = null;
        }
        blurShader = null;
        builtW = 0;
        frontW = 0;
        // The blurred copy is tiny and may still be held by a LiquidGlass; leave it to the garbage collector.
        blur = null;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            handler.removeCallbacks(tick);
            ticking = false;
            startTicking();   // refreshes the time at once, then once a minute
        } else {
            stopTicking();
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------

    private void build(int w, int h) {
        loadPhoto();
        // The clock's outline: condensed bold digits, squeezed narrower, centred, its top at CLOCK_TOP.
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text.setTextSize(h * CLOCK_SIZE);
        clock.reset();
        text.getTextPath(time, 0, time.length(), 0, 0, clock);
        matrix.setScale(CLOCK_SQUEEZE, 1f);
        clock.transform(matrix);
        clock.computeBounds(clockBounds, true);
        matrix.setTranslate(w / 2f - clockBounds.centerX(), h * scene.clockTop - clockBounds.top);
        clock.transform(matrix);
        clock.computeBounds(clockBounds, true);

        float density = getResources().getDisplayMetrics().density;
        // A pale picture gets a navy glass and a stronger white rim, or white digits would vanish into the sky.
        int[] tintColors = scene.lightSky ? new int[]{0x8C2F4F85, 0x662C4A7E, 0x522A4677}
                : new int[]{0x8CFFFFFF, 0x4DFFFFFF, 0x2EFFFFFF};
        int[] rimColors = scene.lightSky ? new int[]{0xFFFFFFFF, 0xCCFFFFFF, 0x99FFFFFF, 0xFFFFFFFF}
                : new int[]{0xF2FFFFFF, 0x59FFFFFF, 0x40FFFFFF, 0xB3FFFFFF};
        glassTint.setShader(new LinearGradient(0, clockBounds.top, 0, clockBounds.bottom,
                tintColors, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        glassRim.setStrokeWidth(Math.max(2f, (scene.lightSky ? 3f : 2.2f) * density));
        glassRim.setShader(new LinearGradient(clockBounds.left, clockBounds.top, clockBounds.right, clockBounds.bottom,
                rimColors, new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        Bitmap small = blur();
        if (small != null) {
            blurShader = new BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(w / (float) small.getWidth(), h / (float) small.getHeight());
            blurShader.setLocalMatrix(m);
            glassFill.setShader(blurShader);
        }

        if (front == null || front.isRecycled() || w != frontW || h != frontH) buildFront(w, h);
        builtW = w;
        builtH = h;
    }

    /** The photograph clipped to the mountains' skyline, only over the clock's region: what hides the digits' lower part. */
    private void buildFront(int w, int h) {
        if (front != null) front.recycle();
        front = null;
        frontW = w;
        frontH = h;
        if (photo == null) return;
        int skylineId = getResources().getIdentifier(scene.skyline, "array", getContext().getPackageName());
        int[] skyline = skylineId == 0 ? new int[0] : getResources().getIntArray(skylineId);
        if (skyline.length < 2) return;
        frontLeft = Math.round(w * FRONT_L);
        frontTop = Math.round(h * FRONT_T);
        int rw = Math.round(w * FRONT_R) - frontLeft, rh = Math.round(h * FRONT_B) - frontTop;
        Bitmap bitmap = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        // The skyline inside the region only (x clamped to its edges), closed along the region's bottom.
        Path silhouette = new Path();
        float step = w * (4f / 1600f);
        silhouette.moveTo(0f, rh + 2f);
        float enterY = rh + 2f;   // the skyline's height where it comes into the region from the left
        float exitY = rh + 2f;    // and where it leaves on the right
        boolean entered = false;
        for (int i = 0; i < skyline.length; i++) {
            float x = i * step - frontLeft;
            float y = skyline[i] / 1000f * h - frontTop;
            if (x < 0f) {
                enterY = y;
                continue;
            }
            if (!entered) {
                silhouette.lineTo(0f, enterY);
                entered = true;
            }
            if (x > rw) {
                exitY = y;
                break;
            }
            silhouette.lineTo(x, y);
            exitY = y;
        }
        silhouette.lineTo(rw, exitY);
        silhouette.lineTo(rw, rh + 2f);
        silhouette.close();
        // The picture's own pixels inside that outline: a clip, then the same stretch the full picture gets.
        float sx = photo.getWidth() / (float) w, sy = photo.getHeight() / (float) h;
        Rect src = new Rect(Math.round(frontLeft * sx), Math.round(frontTop * sy),
                Math.round((frontLeft + rw) * sx), Math.round((frontTop + rh) * sy));
        c.save();
        c.clipPath(silhouette);
        c.drawBitmap(photo, src, new Rect(0, 0, rw, rh), new Paint(Paint.FILTER_BITMAP_FLAG));
        c.restore();
        front = bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        loadPhoto();
        if (photo == null) return;
        if (time.isEmpty()) refreshTime();
        if (w != builtW || h != builtH || builtW == 0) build(w, h);
        dest.set(0, 0, w, h);
        canvas.drawBitmap(photo, null, dest, photoPaint);
        // Darker at the top, where the date and the clock sit.
        shade.setShader(new LinearGradient(0, 0, 0, h * 0.40f, scene.lightSky ? 0x14101030 : 0x66101030, 0x00101030,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.40f, shade);

        // The clock: the picture's blurred copy through the digits, a white tint, then the rim.
        if (blurShader != null) canvas.drawPath(clock, glassFill);
        canvas.drawPath(clock, glassTint);
        canvas.drawPath(clock, glassRim);

        // The mountains in front of the clock's lower part, with the same top shading the sky got.
        if (front != null) canvas.drawBitmap(front, frontLeft, frontTop, null);

        // Darker at the bottom, for the weather and the quote, and for the dock when it comes up.
        shade.setShader(new LinearGradient(0, h * 0.62f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.62f, w, h, shade);
    }
}
