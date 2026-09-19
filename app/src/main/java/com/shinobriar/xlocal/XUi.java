package com.shinobriar.xlocal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

final class XUi {
    static final int BLUE = 0xff1d9bf0;
    static final int PINK = 0xfff91880;
    static final int GREEN = 0xff00ba7c;

    static final class Palette {
        final int bg;
        final int fg;
        final int secondary;
        final int border;
        final int surface;
        final boolean lightStatus;

        Palette(int mode) {
            if (mode == 2) {
                bg = 0xffffffff;
                fg = 0xff0f1419;
                secondary = 0xff536471;
                border = 0xffeff3f4;
                surface = 0xfff7f9f9;
                lightStatus = true;
            } else if (mode == 1) {
                bg = 0xff15202b;
                fg = 0xffffffff;
                secondary = 0xff8b98a5;
                border = 0xff38444d;
                surface = 0xff1e2732;
                lightStatus = false;
            } else {
                bg = 0xff000000;
                fg = 0xffe7e9ea;
                secondary = 0xff71767b;
                border = 0xff2f3336;
                surface = 0xff16181c;
                lightStatus = false;
            }
        }
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans", Typeface.BOLD));
        else t.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        return t;
    }

    static View divider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    static GradientDrawable rounded(int fill, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable stroked(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = rounded(fill, radiusDp, c);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static final class AvatarView extends View {
        private static final Map<String, Bitmap> CACHE = new HashMap<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String name = "?";
        private String path;
        private int color = 0xff536471;

        AvatarView(Context context) {
            super(context);
        }

        AvatarView(Context context, Account a) {
            super(context);
            bind(a);
        }

        void bind(Account a) {
            if (a != null) {
                name = a.name == null ? "?" : a.name;
                path = a.avatarPath;
                color = a.color;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f;

            Bitmap b = bitmap(path);
            if (b != null) {
                int save = canvas.save();
                Path clip = new Path();
                clip.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.clipPath(clip);
                RectF dst = centerCropRect(b.getWidth(), b.getHeight(), getWidth(), getHeight());
                Matrix m = new Matrix();
                float scale = Math.max(getWidth() / (float)b.getWidth(), getHeight() / (float)b.getHeight());
                m.setScale(scale, scale);
                m.postTranslate(dst.left, dst.top);
                canvas.drawBitmap(b, m, paint);
                canvas.restoreToCount(save);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(color);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xffffffff);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(r * 0.9f);
                String initial = name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase();
                Paint.FontMetrics fm = paint.getFontMetrics();
                float y = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(initial, cx, y, paint);
            }
        }

        private Bitmap bitmap(String p) {
            if (p == null || p.isEmpty()) return null;
            if (CACHE.containsKey(p)) return CACHE.get(p);
            File f = new File(p);
            if (!f.exists()) return null;
            Bitmap b = BitmapFactory.decodeFile(p);
            if (b != null) CACHE.put(p, b);
            return b;
        }

        private RectF centerCropRect(int bw, int bh, int vw, int vh) {
            float scale = Math.max(vw / (float) bw, vh / (float) bh);
            float w = bw * scale;
            float h = bh * scale;
            return new RectF((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f);
        }
    }

    static final class IconView extends View {
        static final int HOME = 1;
        static final int SEARCH = 2;
        static final int BELL = 3;
        static final int MAIL = 4;
        static final int REPLY = 5;
        static final int REPOST = 6;
        static final int HEART = 7;
        static final int VIEWS = 8;
        static final int BOOKMARK = 9;
        static final int SHARE = 10;
        static final int MORE = 11;
        static final int BACK = 12;
        static final int PHOTO = 13;
        static final int CLOSE = 14;
        static final int CHECK = 15;
        static final int PLUS = 16;
        static final int XLOGO = 17;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int type;
        private int color;
        private boolean active;

        IconView(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
            p.setStrokeWidth(dp(c, 2f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.STROKE);
        }

        void setIconColor(int c) {
            color = c;
            invalidate();
        }

        void setActive(boolean a) {
            active = a;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f;
            float r = s * 0.32f;
            p.setColor(color);
            p.setStrokeWidth(Math.max(2f, s * 0.07f));
            p.setStyle(Paint.Style.STROKE);
            path.reset();

            switch (type) {
                case HOME:
                    path.moveTo(cx - r, cy - r * .05f);
                    path.lineTo(cx, cy - r);
                    path.lineTo(cx + r, cy - r * .05f);
                    path.lineTo(cx + r * .78f, cy + r);
                    path.lineTo(cx + r * .22f, cy + r);
                    path.lineTo(cx + r * .22f, cy + r * .32f);
                    path.lineTo(cx - r * .22f, cy + r * .32f);
                    path.lineTo(cx - r * .22f, cy + r);
                    path.lineTo(cx - r * .78f, cy + r);
                    path.close();
                    if (active) p.setStyle(Paint.Style.FILL);
                    c.drawPath(path, p);
                    break;
                case SEARCH:
                    c.drawCircle(cx - r * .18f, cy - r * .18f, r * .72f, p);
                    c.drawLine(cx + r * .36f, cy + r * .36f, cx + r, cy + r, p);
                    break;
                case BELL:
                    path.moveTo(cx - r * .75f, cy + r * .45f);
                    path.quadTo(cx - r * .55f, cy + r * .1f, cx - r * .5f, cy - r * .35f);
                    path.quadTo(cx - r * .45f, cy - r, cx, cy - r);
                    path.quadTo(cx + r * .45f, cy - r, cx + r * .5f, cy - r * .35f);
                    path.quadTo(cx + r * .55f, cy + r * .1f, cx + r * .75f, cy + r * .45f);
                    path.lineTo(cx - r * .75f, cy + r * .45f);
                    c.drawPath(path, p);
                    c.drawArc(new RectF(cx-r*.25f,cy+r*.4f,cx+r*.25f,cy+r*.85f),0,180,false,p);
                    break;
                case MAIL:
                    RectF mail = new RectF(cx-r, cy-r*.7f, cx+r, cy+r*.7f);
                    c.drawRoundRect(mail, r*.15f, r*.15f, p);
                    c.drawLine(cx-r, cy-r*.6f, cx, cy+r*.1f, p);
                    c.drawLine(cx+r, cy-r*.6f, cx, cy+r*.1f, p);
                    break;
                case REPLY:
                    c.drawArc(new RectF(cx-r,cy-r*.75f,cx+r*.85f,cy+r*.75f),35,290,false,p);
                    c.drawLine(cx-r*.9f,cy-r*.25f,cx-r*.95f,cy+r*.45f,p);
                    c.drawLine(cx-r*.95f,cy+r*.45f,cx-r*.25f,cy+r*.35f,p);
                    break;
                case REPOST:
                    c.drawLine(cx-r*.85f,cy-r*.42f,cx+r*.55f,cy-r*.42f,p);
                    c.drawLine(cx+r*.55f,cy-r*.42f,cx+r*.25f,cy-r*.72f,p);
                    c.drawLine(cx+r*.55f,cy-r*.42f,cx+r*.25f,cy-r*.12f,p);
                    c.drawLine(cx+r*.85f,cy+r*.42f,cx-r*.55f,cy+r*.42f,p);
                    c.drawLine(cx-r*.55f,cy+r*.42f,cx-r*.25f,cy+r*.72f,p);
                    c.drawLine(cx-r*.55f,cy+r*.42f,cx-r*.25f,cy+r*.12f,p);
                    break;
                case HEART:
                    path.moveTo(cx, cy + r*.82f);
                    path.cubicTo(cx-r*1.15f,cy+r*.05f,cx-r*.9f,cy-r*.85f,cx-r*.32f,cy-r*.82f);
                    path.cubicTo(cx-r*.08f,cy-r*.82f,cx,cy-r*.62f,cx,cy-r*.5f);
                    path.cubicTo(cx,cy-r*.62f,cx+r*.08f,cy-r*.82f,cx+r*.32f,cy-r*.82f);
                    path.cubicTo(cx+r*.9f,cy-r*.85f,cx+r*1.15f,cy+r*.05f,cx,cy+r*.82f);
                    if (active) p.setStyle(Paint.Style.FILL);
                    c.drawPath(path,p);
                    break;
                case VIEWS:
                    for (int i=0;i<4;i++) {
                        float x=cx-r*.85f+i*r*.48f;
                        float top=cy+r*.65f-(i+1)*r*.32f;
                        c.drawLine(x,cy+r*.65f,x,top,p);
                    }
                    break;
                case BOOKMARK:
                    path.moveTo(cx-r*.62f,cy-r);
                    path.lineTo(cx+r*.62f,cy-r);
                    path.lineTo(cx+r*.62f,cy+r);
                    path.lineTo(cx,cy+r*.5f);
                    path.lineTo(cx-r*.62f,cy+r);
                    path.close();
                    if (active) p.setStyle(Paint.Style.FILL);
                    c.drawPath(path,p);
                    break;
                case SHARE:
                    c.drawLine(cx,cy+r*.95f,cx,cy-r*.65f,p);
                    c.drawLine(cx,cy-r*.65f,cx-r*.38f,cy-r*.25f,p);
                    c.drawLine(cx,cy-r*.65f,cx+r*.38f,cy-r*.25f,p);
                    c.drawArc(new RectF(cx-r,cy-r*.05f,cx+r,cy+r),0,180,false,p);
                    break;
                case MORE:
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx-r*.65f,cy,s*.055f,p);
                    c.drawCircle(cx,cy,s*.055f,p);
                    c.drawCircle(cx+r*.65f,cy,s*.055f,p);
                    break;
                case BACK:
                    c.drawLine(cx+r*.55f,cy-r*.75f,cx-r*.4f,cy,p);
                    c.drawLine(cx-r*.4f,cy,cx+r*.55f,cy+r*.75f,p);
                    break;
                case PHOTO:
                    RectF ph = new RectF(cx-r,cy-r*.75f,cx+r,cy+r*.75f);
                    c.drawRoundRect(ph,r*.12f,r*.12f,p);
                    c.drawCircle(cx-r*.38f,cy-r*.25f,r*.16f,p);
                    path.moveTo(cx-r*.75f,cy+r*.45f);
                    path.lineTo(cx-r*.15f,cy-r*.05f);
                    path.lineTo(cx+r*.15f,cy+r*.2f);
                    path.lineTo(cx+r*.48f,cy-r*.12f);
                    path.lineTo(cx+r*.78f,cy+r*.45f);
                    c.drawPath(path,p);
                    break;
                case CLOSE:
                    c.drawLine(cx-r*.7f,cy-r*.7f,cx+r*.7f,cy+r*.7f,p);
                    c.drawLine(cx+r*.7f,cy-r*.7f,cx-r*.7f,cy+r*.7f,p);
                    break;
                case CHECK:
                    c.drawLine(cx-r*.75f,cy,cx-r*.2f,cy+r*.55f,p);
                    c.drawLine(cx-r*.2f,cy+r*.55f,cx+r*.8f,cy-r*.6f,p);
                    break;
                case PLUS:
                    c.drawLine(cx-r*.8f,cy,cx+r*.8f,cy,p);
                    c.drawLine(cx,cy-r*.8f,cx,cy+r*.8f,p);
                    break;
                case XLOGO:
                    p.setStrokeWidth(Math.max(3f,s*.11f));
                    c.drawLine(cx-r*.8f,cy-r,cx+r*.8f,cy+r,p);
                    c.drawLine(cx+r*.72f,cy-r,cx+r*.08f,cy-r*.18f,p);
                    c.drawLine(cx-r*.72f,cy+r,cx-r*.08f,cy+r*.18f,p);
                    break;
            }
        }
    }
}
