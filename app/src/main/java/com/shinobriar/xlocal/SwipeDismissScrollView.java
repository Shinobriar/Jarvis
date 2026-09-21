package com.shinobriar.xlocal;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

final class SwipeDismissScrollView extends ScrollView {
    private float downX;
    private float downY;
    private Runnable onSwipeLeft;

    SwipeDismissScrollView(Context context) {
        super(context);
    }

    void setOnSwipeLeft(Runnable listener) {
        onSwipeLeft = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                float threshold = 64f * getResources().getDisplayMetrics().density;
                if (dx <= -threshold && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    if (onSwipeLeft != null) onSwipeLeft.run();
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
