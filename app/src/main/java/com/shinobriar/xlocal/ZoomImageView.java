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

final class ZoomImageView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private Bitmap bitmap;
    private float minScale = 1f;
    private float maxScale = 8f;

    ZoomImageView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        if (bitmap == null) return false;
                        float current = currentScale();
                        float desired = Math.max(minScale,
                                Math.min(maxScale, current * detector.getScaleFactor()));
                        float factor = desired / Math.max(0.0001f, current);
                        matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                        constrain();
                        invalidate();
                        return true;
                    }
                });
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) { return true; }

                    @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                        if (bitmap == null || scaleDetector.isInProgress()) return false;
                        matrix.postTranslate(-dx, -dy);
                        constrain();
                        invalidate();
                        return true;
                    }

                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (bitmap == null) return false;
                        if (currentScale() > minScale * 1.05f) {
                            resetMatrix();
                        } else {
                            float target = Math.min(maxScale, minScale * 2.5f);
                            float factor = target / Math.max(0.0001f, currentScale());
                            matrix.postScale(factor, factor, e.getX(), e.getY());
                            constrain();
                            invalidate();
                        }
                        return true;
                    }
                });
    }

    void setBitmap(Bitmap value) {
        bitmap = value;
        resetMatrix();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        resetMatrix();
    }

    private void resetMatrix() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        matrix.reset();
        minScale = Math.min(getWidth() / (float)Math.max(1, bitmap.getWidth()),
                getHeight() / (float)Math.max(1, bitmap.getHeight()));
        maxScale = Math.max(minScale * 8f, minScale + 0.01f);
        matrix.postScale(minScale, minScale);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * minScale) / 2f,
                (getHeight() - bitmap.getHeight() * minScale) / 2f);
        invalidate();
    }

    private float currentScale() {
        float[] values = new float[9];
        matrix.getValues(values);
        return Math.abs(values[Matrix.MSCALE_X]);
    }

    private RectF imageBounds() {
        if (bitmap == null) return new RectF();
        RectF bounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(bounds);
        return bounds;
    }

    private void constrain() {
        RectF bounds = imageBounds();
        float dx;
        float dy;
        if (bounds.width() <= getWidth()) {
            dx = getWidth() / 2f - bounds.centerX();
        } else if (bounds.left > 0) {
            dx = -bounds.left;
        } else if (bounds.right < getWidth()) {
            dx = getWidth() - bounds.right;
        } else {
            dx = 0;
        }

        if (bounds.height() <= getHeight()) {
            dy = getHeight() / 2f - bounds.centerY();
        } else if (bounds.top > 0) {
            dy = -bounds.top;
        } else if (bounds.bottom < getHeight()) {
            dy = getHeight() - bounds.bottom;
        } else {
            dy = 0;
        }
        matrix.postTranslate(dx, dy);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null && !bitmap.isRecycled()) canvas.drawBitmap(bitmap, matrix, paint);
    }
}
