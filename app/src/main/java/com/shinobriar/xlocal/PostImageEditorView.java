package com.shinobriar.xlocal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;

final class PostImageEditorView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final ArrayDeque<Bitmap> history = new ArrayDeque<>();

    private Bitmap original;
    private Bitmap working;
    private boolean drawMode;
    private int brushColor = Color.WHITE;
    private int filterIndex;
    private float lastX;
    private float lastY;
    private boolean drawing;

    PostImageEditorView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        brushPaint.setStyle(Paint.Style.STROKE);
        brushPaint.setStrokeCap(Paint.Cap.ROUND);
        brushPaint.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(true);
    }

    void setBitmap(Bitmap source) {
        if (source == null) return;
        Bitmap scaled = downscale(source, 2048);
        original = scaled.copy(Bitmap.Config.ARGB_8888, true);
        working = scaled.copy(Bitmap.Config.ARGB_8888, true);
        if (scaled != source && !source.isRecycled()) source.recycle();
        history.clear();
        filterIndex = 0;
        invalidate();
    }

    Bitmap getWorkingCopy() {
        return working == null ? null : working.copy(Bitmap.Config.ARGB_8888, true);
    }

    void replaceWorking(Bitmap bitmap, boolean remember) {
        if (bitmap == null) return;
        if (remember) snapshot();
        Bitmap scaled = downscale(bitmap, 2048);
        if (working != null && working != original && !working.isRecycled()) working.recycle();
        working = scaled.copy(Bitmap.Config.ARGB_8888, true);
        if (scaled != bitmap && !bitmap.isRecycled()) bitmap.recycle();
        invalidate();
    }

    void setDrawMode(boolean enabled) {
        drawMode = enabled;
        drawing = false;
    }

    boolean isDrawMode() { return drawMode; }

    void setBrushColor(int color) {
        brushColor = color;
    }

    String cycleFilter() {
        filterIndex = (filterIndex + 1) % 4;
        invalidate();
        switch (filterIndex) {
            case 1: return "Mono";
            case 2: return "Warm";
            case 3: return "Cool";
            default: return "Original";
        }
    }

    void addText(String text) {
        if (working == null || text == null || text.trim().isEmpty()) return;
        snapshot();
        Canvas canvas = new Canvas(working);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextSize(Math.max(36f, working.getWidth() * 0.055f));
        p.setShadowLayer(Math.max(3f, working.getWidth() * .004f), 0, 2, Color.BLACK);

        String value = text.trim();
        float max = working.getWidth() * .86f;
        while (p.measureText(value) > max && p.getTextSize() > 22f) p.setTextSize(p.getTextSize() - 2f);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = working.getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(value, working.getWidth() / 2f, y, p);
        invalidate();
    }

    void rotate90() {
        if (working == null) return;
        snapshot();
        Matrix m = new Matrix();
        m.postRotate(90f);
        Bitmap rotated = Bitmap.createBitmap(working, 0, 0, working.getWidth(), working.getHeight(), m, true);
        if (working != null && working != original && !working.isRecycled()) working.recycle();
        working = rotated;
        invalidate();
    }

    boolean undo() {
        if (history.isEmpty()) return false;
        if (working != null && working != original && !working.isRecycled()) working.recycle();
        working = history.removeLast();
        invalidate();
        return true;
    }

    void reset() {
        if (original == null) return;
        snapshot();
        if (working != null && working != original && !working.isRecycled()) working.recycle();
        working = original.copy(Bitmap.Config.ARGB_8888, true);
        filterIndex = 0;
        drawMode = false;
        invalidate();
    }

    Bitmap renderFinal() {
        if (working == null) return null;
        Bitmap out = Bitmap.createBitmap(working.getWidth(), working.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(filterForIndex(filterIndex));
        c.drawBitmap(working, 0, 0, p);
        return out;
    }

    private void snapshot() {
        if (working == null) return;
        history.addLast(working.copy(Bitmap.Config.ARGB_8888, true));
        while (history.size() > 6) {
            Bitmap old = history.removeFirst();
            if (!old.isRecycled()) old.recycle();
        }
    }

    private static Bitmap downscale(Bitmap source, int maxSide) {
        int w = source.getWidth(), h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return source;
        float scale = maxSide / (float) max;
        return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
    }

    private ColorMatrixColorFilter filterForIndex(int index) {
        ColorMatrix cm = new ColorMatrix();
        if (index == 1) {
            cm.setSaturation(0f);
        } else if (index == 2) {
            cm.set(new float[]{
                    1.10f,0,0,0,7,
                    0,1.03f,0,0,2,
                    0,0,.90f,0,-3,
                    0,0,0,1,0
            });
        } else if (index == 3) {
            cm.set(new float[]{
                    .90f,0,0,0,-2,
                    0,1.02f,0,0,0,
                    0,0,1.10f,0,7,
                    0,0,0,1,0
            });
        } else {
            return null;
        }
        return new ColorMatrixColorFilter(cm);
    }

    private void computeImageRect() {
        if (working == null || getWidth() <= 0 || getHeight() <= 0) {
            imageRect.setEmpty();
            return;
        }
        float scale = Math.min(getWidth() / (float)working.getWidth(), getHeight() / (float)working.getHeight());
        float w = working.getWidth() * scale;
        float h = working.getHeight() * scale;
        imageRect.set((getWidth()-w)/2f, (getHeight()-h)/2f, (getWidth()+w)/2f, (getHeight()+h)/2f);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (working == null) return;
        computeImageRect();
        imagePaint.setColorFilter(filterForIndex(filterIndex));
        canvas.drawBitmap(working, null, imageRect, imagePaint);
        imagePaint.setColorFilter(null);
    }

    private boolean mapToBitmap(float x, float y, float[] out) {
        computeImageRect();
        if (working == null || !imageRect.contains(x,y)) return false;
        out[0] = (x - imageRect.left) / imageRect.width() * working.getWidth();
        out[1] = (y - imageRect.top) / imageRect.height() * working.getHeight();
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!drawMode || working == null) return true;
        float[] mapped = new float[2];
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (!mapToBitmap(event.getX(), event.getY(), mapped)) return true;
            snapshot();
            lastX = mapped[0];
            lastY = mapped[1];
            drawing = true;
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && drawing) {
            if (!mapToBitmap(event.getX(), event.getY(), mapped)) return true;
            Canvas c = new Canvas(working);
            brushPaint.setColor(brushColor);
            float displayScale = imageRect.width() / Math.max(1f, working.getWidth());
            brushPaint.setStrokeWidth(Math.max(4f, 7f / Math.max(.15f, displayScale)));
            c.drawLine(lastX, lastY, mapped[0], mapped[1], brushPaint);
            lastX = mapped[0];
            lastY = mapped[1];
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            drawing = false;
            return true;
        }
        return true;
    }
}
