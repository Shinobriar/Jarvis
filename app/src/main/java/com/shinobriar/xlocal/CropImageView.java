package com.shinobriar.xlocal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class CropImageView extends View {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF crop = new RectF();
    private Bitmap bitmap;
    private float aspect = 1f;
    private float minScale = 1f;
    private float maxScale = 8f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    CropImageView(Context context) {
        super(context);
        shadePaint.setColor(0x99000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(XUi.dp(context, 2));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (bitmap == null) return false;
                float current = currentScale();
                float desired = current * detector.getScaleFactor();
                float clamped = Math.max(minScale, Math.min(maxScale, desired));
                float factor = clamped / Math.max(0.0001f, current);
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                clampToCrop();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (bitmap == null) return false;
                matrix.postTranslate(-dx, -dy);
                clampToCrop();
                invalidate();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                resetMatrix();
                return true;
            }
        });
    }

    void setBitmap(Bitmap value, float cropAspect) {
        bitmap = value;
        aspect = cropAspect <= 0 ? 1f : cropAspect;
        if (getWidth() > 0 && getHeight() > 0) {
            computeCrop();
            resetMatrix();
        }
        invalidate();
    }

    Bitmap getBitmap() { return bitmap; }

    void rotate90() {
        if (bitmap == null) return;
        Matrix r = new Matrix();
        r.postRotate(90f);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), r, true);
        if (rotated != bitmap) {
            try { bitmap.recycle(); } catch (Exception ignored) {}
        }
        bitmap = rotated;
        resetMatrix();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        computeCrop();
        resetMatrix();
    }

    private void computeCrop() {
        float margin = XUi.dp(getContext(), 18);
        float availableW = Math.max(1, getWidth() - margin * 2);
        float availableH = Math.max(1, getHeight() - margin * 2);
        float cw = availableW;
        float ch = cw / aspect;
        if (ch > availableH) {
            ch = availableH;
            cw = ch * aspect;
        }
        float left = (getWidth() - cw) / 2f;
        float top = (getHeight() - ch) / 2f;
        crop.set(left, top, left + cw, top + ch);
    }

    private void resetMatrix() {
        if (bitmap == null || crop.width() <= 0 || crop.height() <= 0) return;
        matrix.reset();
        minScale = Math.max(crop.width() / bitmap.getWidth(), crop.height() / bitmap.getHeight());
        maxScale = Math.max(minScale * 8f, minScale + 0.01f);
        matrix.postScale(minScale, minScale);
        float sw = bitmap.getWidth() * minScale;
        float sh = bitmap.getHeight() * minScale;
        matrix.postTranslate(crop.centerX() - sw / 2f, crop.centerY() - sh / 2f);
        clampToCrop();
        invalidate();
    }

    private float currentScale() {
        float[] v = new float[9];
        matrix.getValues(v);
        return Math.abs(v[Matrix.MSCALE_X]);
    }

    private RectF mappedBitmapRect() {
        if (bitmap == null) return new RectF();
        RectF r = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(r);
        return r;
    }

    private void clampToCrop() {
        if (bitmap == null) return;
        RectF r = mappedBitmapRect();
        float dx = 0, dy = 0;
        if (r.left > crop.left) dx = crop.left - r.left;
        if (r.right < crop.right) dx = crop.right - r.right;
        if (r.top > crop.top) dy = crop.top - r.top;
        if (r.bottom < crop.bottom) dy = crop.bottom - r.bottom;
        matrix.postTranslate(dx, dy);
    }

    Bitmap renderCrop(int outWidth, int outHeight) {
        if (bitmap == null) return null;
        Matrix inv = new Matrix();
        if (!matrix.invert(inv)) return null;
        RectF src = new RectF(crop);
        inv.mapRect(src);
        int left = Math.max(0, Math.min(bitmap.getWidth() - 1, (int)Math.floor(src.left)));
        int top = Math.max(0, Math.min(bitmap.getHeight() - 1, (int)Math.floor(src.top)));
        int right = Math.max(left + 1, Math.min(bitmap.getWidth(), (int)Math.ceil(src.right)));
        int bottom = Math.max(top + 1, Math.min(bitmap.getHeight(), (int)Math.ceil(src.bottom)));
        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        Bitmap out = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true);
        if (out != cropped) cropped.recycle();
        return out;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean a = scaleDetector.onTouchEvent(event);
        boolean b = gestureDetector.onTouchEvent(event);
        return a || b || super.onTouchEvent(event);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        if (bitmap != null) canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        if (crop.width() > 0) {
            canvas.drawRect(0, 0, getWidth(), crop.top, shadePaint);
            canvas.drawRect(0, crop.bottom, getWidth(), getHeight(), shadePaint);
            canvas.drawRect(0, crop.top, crop.left, crop.bottom, shadePaint);
            canvas.drawRect(crop.right, crop.top, getWidth(), crop.bottom, shadePaint);
            canvas.drawRect(crop, borderPaint);
        }
    }
}
