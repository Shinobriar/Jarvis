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
import android.graphics.drawable.Drawable;
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
        final int bg, fg, secondary, border, surface;
        final boolean lightStatus;

        Palette(int mode) {
            if (mode == 2) {
                bg = 0xffffffff; fg = 0xff0f1419; secondary = 0xff536471;
                border = 0xffeff3f4; surface = 0xfff7f9f9; lightStatus = true;
            } else if (mode == 1) {
                bg = 0xff15202b; fg = 0xffffffff; secondary = 0xff8b98a5;
                border = 0xff38444d; surface = 0xff1e2732; lightStatus = false;
            } else {
                bg = 0xff000000; fg = 0xffe7e9ea; secondary = 0xff71767b;
                border = 0xff2f3336; surface = 0xff16181c; lightStatus = false;
            }
        }
    }

    static int dp(Context c, float v) {
        return (int)(v * c.getResources().getDisplayMetrics().density + .5f);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
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
        private String imagePath;
        private int color = 0xff536471;

        AvatarView(Context context) { super(context); }
        AvatarView(Context context, Account account) { super(context); bind(account); }

        void bind(Account a) {
            if (a != null) {
                name = a.name == null ? "?" : a.name;
                imagePath = a.avatarPath;
                color = a.color;
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth()/2f, cy = getHeight()/2f;
            float r = Math.min(getWidth(), getHeight())/2f;
            Bitmap b = bitmap(imagePath);
            if (b != null) {
                int save = canvas.save();
                Path clip = new Path();
                clip.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.clipPath(clip);
                float scale = Math.max(getWidth()/(float)b.getWidth(), getHeight()/(float)b.getHeight());
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate((getWidth()-b.getWidth()*scale)/2f, (getHeight()-b.getHeight()*scale)/2f);
                canvas.drawBitmap(b, m, paint);
                canvas.restoreToCount(save);
            } else {
                paint.setColor(color);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xffffffff);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(r*.9f);
                Paint.FontMetrics fm = paint.getFontMetrics();
                String initial = name.trim().isEmpty() ? "?" : name.trim().substring(0,1).toUpperCase();
                canvas.drawText(initial, cx, cy-(fm.ascent+fm.descent)/2f, paint);
            }
        }

        private Bitmap bitmap(String p) {
            if (p == null || p.isEmpty()) return null;
            Bitmap cached = CACHE.get(p);
            if (cached != null) return cached;
            File f = new File(p);
            if (!f.exists()) return null;
            Bitmap b = BitmapFactory.decodeFile(p);
            if (b != null) CACHE.put(p,b);
            return b;
        }
    }

    /**
     * Uses vector path data extracted from the user-supplied X 12.23.1 APK.
     * This replaces the hand-drawn approximation from v1.
     */
    static final class IconView extends View {
        static final int HOME=1, SEARCH=2, BELL=3, MAIL=4, REPLY=5, REPOST=6,
                HEART=7, VIEWS=8, BOOKMARK=9, SHARE=10, MORE=11, BACK=12,
                PHOTO=13, CLOSE=14, CHECK=15, PLUS=16, XLOGO=17, COMPOSE=18,
                VERIFIED=19, PROFILE=20, GLOBE=21, CAMERA=22, GIF=23, POLL=24,
                LOCATION=25, SCHEDULE=26, PLUS_CIRCLE=27, CALENDAR=28, LINK=29,
                DRAFTS=30, SETTINGS=31, BALLOON=32, CROP=33, DRAW=34, FILTER=35,
                PLAY=36, PAUSE=37, SOUND=38, SOUND_OFF=39, MEDIA_EXPAND=40,
                GROUP_ADD=41, PEOPLE_GROUP=42, COMPOSE_DM=43;

        private final int type;
        private int color;
        private boolean active;
        private Drawable drawable;

        IconView(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
            setWillNotDraw(false);
            reload();
        }

        void setIconColor(int c) { color=c; reload(); }
        void setActive(boolean a) { active=a; reload(); }

        private int resForType() {
            switch (type) {
                case HOME: return active ? R.drawable.ic_vector_home : R.drawable.ic_vector_home_stroke;
                case SEARCH: return active ? R.drawable.ic_vector_search : R.drawable.ic_vector_search_stroke;
                case BELL: return active ? R.drawable.ic_vector_notifications : R.drawable.ic_vector_notifications_stroke;
                case MAIL: return active ? R.drawable.ic_vector_messages : R.drawable.ic_vector_messages_stroke;
                case PROFILE: return active ? R.drawable.ic_vector_person : R.drawable.ic_vector_person_stroke;
                case REPLY: return R.drawable.ic_vector_reply_stroke;
                case REPOST: return active ? R.drawable.ic_vector_retweet : R.drawable.ic_vector_retweet_stroke;
                case HEART: return active ? R.drawable.ic_vector_heart : R.drawable.ic_vector_heart_stroke;
                case VIEWS: return R.drawable.ic_vector_bar_chart;
                case BOOKMARK: return active ? R.drawable.ic_vector_bookmark : R.drawable.ic_vector_bookmark_stroke;
                case SHARE: return R.drawable.ic_vector_share_stroke;
                case MORE: return R.drawable.ic_vector_overflow;
                case BACK: return R.drawable.ic_vector_arrow_left;
                case PHOTO: return R.drawable.ic_vector_photo;
                case CLOSE: return R.drawable.ic_vector_close_nomargin;
                case CHECK: return R.drawable.ic_vector_checkmark;
                case PLUS: return R.drawable.ic_vector_person_add;
                case COMPOSE: return R.drawable.ic_vector_compose;
                case VERIFIED: return R.drawable.ic_vector_verified;
                case XLOGO: return R.drawable.ic_vector_x;
                case GLOBE: return R.drawable.ic_vector_globe_stroke;
                case CAMERA: return R.drawable.ic_vector_camera;
                case GIF: return R.drawable.ic_vector_gif_compose;
                case POLL: return R.drawable.ic_vector_bulleted_list;
                case LOCATION: return R.drawable.ic_vector_location_stroke;
                case SCHEDULE: return R.drawable.ic_vector_schedule;
                case PLUS_CIRCLE: return R.drawable.ic_vector_plus_circle_fill;
                case CALENDAR: return R.drawable.ic_vector_calendar;
                case LINK: return R.drawable.ic_vector_link;
                case DRAFTS: return R.drawable.ic_vector_drafts;
                case SETTINGS: return R.drawable.ic_vector_settings_stroke;
                case BALLOON: return R.drawable.ic_vector_balloon_stroke;
                case CROP: return R.drawable.ic_vector_photo_crop;
                case DRAW: return R.drawable.ic_vector_draw;
                case FILTER: return R.drawable.ic_vector_filter;
                case PLAY: return R.drawable.ic_vector_play;
                case PAUSE: return R.drawable.ic_vector_pause;
                case SOUND: return R.drawable.ic_vector_sound;
                case SOUND_OFF: return R.drawable.ic_vector_sound_off;
                case MEDIA_EXPAND: return R.drawable.ic_vector_media_expand;
                case GROUP_ADD: return R.drawable.ic_vector_group_add;
                case PEOPLE_GROUP: return R.drawable.ic_vector_people_group_stroke;
                case COMPOSE_DM: return R.drawable.ic_vector_compose_dm;
                default: return R.drawable.ic_vector_x;
            }
        }

        private void reload() {
            try {
                drawable = getContext().getDrawable(resForType()).mutate();
                drawable.setTint(type == VERIFIED ? BLUE : color);
            } catch (Exception e) {
                drawable = null;
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (drawable == null) return;
            int left=getPaddingLeft(), top=getPaddingTop();
            int right=getWidth()-getPaddingRight(), bottom=getHeight()-getPaddingBottom();
            int w=Math.max(0,right-left), h=Math.max(0,bottom-top);
            int size=Math.min(w,h);
            int x=left+(w-size)/2, y=top+(h-size)/2;
            drawable.setBounds(x,y,x+size,y+size);
            drawable.draw(canvas);
        }
    }
}
