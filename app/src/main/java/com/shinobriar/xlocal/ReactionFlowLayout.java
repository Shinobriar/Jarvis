package com.shinobriar.xlocal;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

final class ReactionFlowLayout extends ViewGroup {
    private final int gap;
    private int preferredWidth;

    ReactionFlowLayout(Context context) {
        super(context);
        gap = XUi.dp(context, 4);
    }

    void setPreferredWidth(int width) {
        preferredWidth = Math.max(0, width);
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) available = Integer.MAX_VALUE;
        int limit = preferredWidth > 0 ? Math.min(available, preferredWidth) : available;
        if (limit <= 0) limit = available;

        int x = 0;
        int y = 0;
        int lineHeight = 0;
        int usedWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > 0 && x + childWidth > limit) {
                y += lineHeight + gap;
                x = 0;
                lineHeight = 0;
            }
            usedWidth = Math.max(usedWidth, x + childWidth);
            x += childWidth + gap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
        int measuredWidth = Math.min(available, Math.max(0, preferredWidth > 0 ? preferredWidth : usedWidth));
        setMeasuredDimension(resolveSize(measuredWidth, widthMeasureSpec),
                resolveSize(y + lineHeight, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int limit = Math.max(1, getMeasuredWidth());
        int x = 0;
        int y = 0;
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > 0 && x + childWidth > limit) {
                y += lineHeight + gap;
                x = 0;
                lineHeight = 0;
            }
            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + gap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}
