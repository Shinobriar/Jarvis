package com.shinobriar.xlocal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

final class AspectTextureView extends TextureView {
    private int videoWidth;
    private int videoHeight;

    AspectTextureView(Context context) {
        super(context);
    }

    AspectTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setVideoSize(int width, int height) {
        videoWidth = Math.max(0, width);
        videoHeight = Math.max(0, height);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (videoWidth > 0 && videoHeight > 0 && width > 0 && height > 0) {
            float viewAspect = width / (float) height;
            float videoAspect = videoWidth / (float) videoHeight;
            if (videoAspect > viewAspect) {
                height = Math.max(1, Math.round(width / videoAspect));
            } else {
                width = Math.max(1, Math.round(height * videoAspect));
            }
        }
        setMeasuredDimension(width, height);
    }
}
