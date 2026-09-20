package com.shinobriar.xlocal;

import android.content.Context;
import android.widget.FrameLayout;

final class AspectFrameLayout extends FrameLayout {
    private float aspectRatio = 1f;

    AspectFrameLayout(Context context) {
        super(context);
    }

    void setAspectRatio(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio) || ratio <= 0f) ratio = 1f;
        aspectRatio = Math.max(0.28f, Math.min(3.2f, ratio));
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int desiredHeight = Math.max(1, Math.round(width / aspectRatio));
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) desiredHeight = heightSize;
        else if (heightMode == MeasureSpec.AT_MOST) desiredHeight = Math.min(desiredHeight, heightSize);

        int exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int exactHeight = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY);
        super.onMeasure(exactWidth, exactHeight);
    }
}
