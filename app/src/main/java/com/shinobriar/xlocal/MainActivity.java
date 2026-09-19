package com.shinobriar.xlocal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_AVATAR = 501;
    private static final int PICK_BANNER = 502;
    private static final int PICK_POST_MEDIA = 503;
    private static final int EXPORT_UNIVERSE = 504;
    private static final int IMPORT_UNIVERSE = 505;
    private static final int SAVE_POST_MEDIA = 506;

    private static final int SCREEN_HOME = 1;
    private static final int SCREEN_SEARCH = 2;
    private static final int SCREEN_NOTIFICATIONS = 3;
    private static final int SCREEN_MESSAGES = 4;
    private static final int SCREEN_PROFILE = 5;
    private static final int SCREEN_POST = 6;
    private static final int SCREEN_BOOKMARKS = 7;
    private static final int SCREEN_CHAT = 8;
    private static final int SCREEN_DRAFTS = 9;
    private static final int SCREEN_FOLLOW_LIST = 10;

    private LocalDb db;
    private SharedPreferences prefs;
    private BotEngine botEngine;
    private final Random random = new Random();
    private XUi.Palette pal;
    private int themeMode;
    private long currentAccountId;

    private int currentScreen = SCREEN_HOME;
    private boolean homeForYou = true;
    private long currentProfileId = -1;
    private int profileTab = 0;
    private long currentPostId = -1;
    private long currentChatId = -1;
    private boolean currentFollowListFollowing = true;

    private long pendingImageAccountId = -1;
    private String pendingSaveMediaPath;
    private String composeDraft = "";
    private String composeMediaPath;
    private long composeAuthorId = -1;
    private Long composeReplyTo;
    private Long composeQuoteOf;
    private long activeDraftId = -1;

    private final Map<Long, Account> accountCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("xlocal_prefs", MODE_PRIVATE);
        themeMode = prefs.getInt("theme_mode", 0);
        pal = new XUi.Palette(themeMode);
        db = new LocalDb(this);
        currentAccountId = prefs.getLong("current_account", -1);
        ensureCurrentAccount();
        applySystemBars();
        botEngine = new BotEngine(this, db, prefs, () -> {
            if (!isFinishing() && currentScreen == SCREEN_HOME) renderHome();
        });
        botEngine.start();
        renderHome();
    }

    @Override
    protected void onDestroy() {
        if (botEngine != null) botEngine.shutdown();
        super.onDestroy();
    }

    private void ensureCurrentAccount() {
        if (currentAccountId > 0 && db.getAccount(currentAccountId) != null) return;
        List<Account> all = db.listAccounts();
        if (!all.isEmpty()) {
            currentAccountId = all.get(0).id;
            prefs.edit().putLong("current_account", currentAccountId).apply();
        }
    }

    private void applySystemBars() {
        Window w = getWindow();
        w.setStatusBarColor(pal.bg);
        w.setNavigationBarColor(pal.bg);
        int flags = 0;
        if (pal.lightStatus) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (pal.lightStatus && Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float v) {
        return XUi.dp(this, v);
    }

    private Account account(long id) {
        Account a = accountCache.get(id);
        if (a == null) {
            a = db.getAccount(id);
            if (a != null) accountCache.put(id, a);
        }
        return a;
    }

    private void clearAccountCache() {
        accountCache.clear();
    }

    private LinearLayout vbox() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundColor(pal.bg);
        return v;
    }

    private LinearLayout hbox() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(pal.bg);
        return h;
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        return XUi.text(this, text, size, color, bold);
    }

    private void applyMentionLinks(TextView view, String text) {
        SpannableStringBuilder s = new SpannableStringBuilder(text == null ? "" : text);
        Matcher m = Pattern.compile("@([A-Za-z0-9_]{1,30})").matcher(s);
        while (m.find()) {
            Account target = db.getAccountByHandle(m.group(1));
            if (target == null || !db.canSeeAccount(currentAccountId, target.id)) continue;
            final long targetId = target.id;
            s.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { renderProfile(targetId); }
                @Override public void updateDrawState(TextPaint ds) {
                    ds.setColor(XUi.BLUE);
                    ds.setUnderlineText(false);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(s);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Color.TRANSPARENT);
    }

    private void wireMentionAutocomplete(EditText body, LinearLayout suggestions, ScrollView suggestionsScroll) {
        final boolean[] internal = {false};
        body.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable e) {
                if (internal[0]) return;
                ForegroundColorSpan[] old = e.getSpans(0, e.length(), ForegroundColorSpan.class);
                for (ForegroundColorSpan span : old) e.removeSpan(span);
                Matcher all = Pattern.compile("@[A-Za-z0-9_]{1,30}").matcher(e);
                while (all.find()) {
                    Account found = db.getAccountByHandle(all.group().substring(1));
                    if (found != null && db.canSeeAccount(currentAccountId, found.id)) {
                        e.setSpan(new ForegroundColorSpan(XUi.BLUE), all.start(), all.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                int cursor = body.getSelectionStart();
                if (cursor < 0 || cursor > e.length()) {
                    suggestionsScroll.setVisibility(View.GONE);
                    return;
                }
                int start = cursor;
                while (start > 0 && !Character.isWhitespace(e.charAt(start - 1))) start--;
                if (start >= cursor || e.charAt(start) != '@') {
                    suggestionsScroll.setVisibility(View.GONE);
                    return;
                }
                String token = e.subSequence(start + 1, cursor).toString();
                if (!token.matches("[A-Za-z0-9_]*")) {
                    suggestionsScroll.setVisibility(View.GONE);
                    return;
                }

                List<Account> matches = db.searchAccounts(token, currentAccountId);
                suggestions.removeAllViews();
                int shown = 0;
                for (Account a : matches) {
                    if (shown++ >= 12) break;
                    LinearLayout row = hbox();
                    row.setPadding(dp(10), dp(7), dp(10), dp(7));
                    XUi.AvatarView av = new XUi.AvatarView(MainActivity.this, a);
                    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(34), dp(34));
                    ap.setMargins(0, 0, dp(9), 0);
                    av.setLayoutParams(ap);
                    row.addView(av);
                    LinearLayout labels = vbox();
                    labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    labels.addView(tv(a.name, 14, pal.fg, true));
                    labels.addView(tv("@" + a.handle, 13, pal.secondary, false));
                    row.addView(labels);
                    final int tokenStart = start;
                    final String replacement = "@" + a.handle + " ";
                    row.setOnClickListener(v -> {
                        internal[0] = true;
                        Editable now = body.getText();
                        int end = Math.min(body.getSelectionStart(), now.length());
                        now.replace(tokenStart, end, replacement);
                        body.setSelection(tokenStart + replacement.length());
                        internal[0] = false;
                        suggestionsScroll.setVisibility(View.GONE);
                    });
                    suggestions.addView(row);
                }
                suggestionsScroll.setVisibility(suggestions.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private View space(int widthDp, int heightDp) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return s;
    }

    private XUi.IconView verifiedBadge(int sizeDp) {
        XUi.IconView badge = new XUi.IconView(this, XUi.IconView.VERIFIED, XUi.BLUE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.setMargins(dp(3), 0, 0, 0);
        badge.setLayoutParams(lp);
        badge.setPadding(dp(1), dp(1), dp(1), dp(1));
        return badge;
    }

    private TextView pill(String text, boolean filled) {
        TextView t = tv(text, 14, filled ? (themeMode == 2 ? Color.WHITE : Color.BLACK) : pal.fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        if (filled) {
            int fill = themeMode == 2 ? 0xff0f1419 : 0xffeff3f4;
            t.setBackground(XUi.rounded(fill, 999, this));
        } else {
            t.setBackground(XUi.stroked(Color.TRANSPARENT, pal.border, 999, this));
        }
        t.setMinHeight(dp(36));
        return t;
    }

    private void setScreen(View root) {
        setContentView(root);
    }

    private FrameLayout baseFrame() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(pal.bg);
        f.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }

    private LinearLayout topBar(String title, boolean back) {
        LinearLayout bar = hbox();
        bar.setPadding(dp(8), 0, dp(8), 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(53)));

        if (back) {
            XUi.IconView iv = new XUi.IconView(this, XUi.IconView.BACK, pal.fg);
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
            iv.setPadding(dp(10), dp(10), dp(10), dp(10));
            iv.setOnClickListener(v -> goBackFromSubscreen());
            bar.addView(iv);
        } else {
            Account me = account(currentAccountId);
            XUi.AvatarView av = new XUi.AvatarView(this, me);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(32), dp(32));
            ap.setMargins(dp(8), 0, dp(12), 0);
            av.setLayoutParams(ap);
            av.setOnClickListener(v -> showAccountSwitcher());
            bar.addView(av);
        }

        TextView t = tv(title, 20, pal.fg, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        t.setLayoutParams(tp);
        if (!back && title.isEmpty()) t.setGravity(Gravity.CENTER);
        bar.addView(t);
        return bar;
    }

    private LinearLayout xLogoTopBar() {
        LinearLayout bar = hbox();
        bar.setPadding(dp(8), 0, dp(8), 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(53)));

        Account me = account(currentAccountId);
        XUi.AvatarView av = new XUi.AvatarView(this, me);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(32), dp(32));
        ap.setMargins(dp(8), 0, 0, 0);
        av.setLayoutParams(ap);
        av.setOnClickListener(v -> showAccountSwitcher());
        bar.addView(av);

        FrameLayout center = new FrameLayout(this);
        center.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        XUi.IconView x = new XUi.IconView(this, XUi.IconView.XLOGO, pal.fg);
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(31), dp(31), Gravity.CENTER);
        x.setLayoutParams(xp);
        x.setOnLongClickListener(v -> {
            Toast.makeText(this, "Director Mode: long-press any post or profile", Toast.LENGTH_SHORT).show();
            return true;
        });
        center.addView(x);
        bar.addView(center);

        Space rightBalance = new Space(this);
        rightBalance.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(40)));
        bar.addView(rightBalance);

        return bar;
    }

    private View tab(String label, boolean selected, View.OnClickListener click) {
        LinearLayout wrap = vbox();
        wrap.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, dp(53), 1f));
        TextView t = tv(label, 15, selected ? pal.fg : pal.secondary, selected);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(t);
        View line = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(4));
        line.setLayoutParams(lp);
        line.setBackground(selected ? XUi.rounded(XUi.BLUE, 999, this) : new ColorDrawable(Color.TRANSPARENT));
        wrap.addView(line);
        wrap.setOnClickListener(click);
        return wrap;
    }

    private LinearLayout bottomNav(int selected) {
        LinearLayout nav = hbox();
        nav.setGravity(Gravity.CENTER);
        nav.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        nav.setBackgroundColor(pal.bg);

        addNavIcon(nav, XUi.IconView.HOME, selected == SCREEN_HOME, () -> renderHome());
        addNavIcon(nav, XUi.IconView.SEARCH, selected == SCREEN_SEARCH, () -> renderSearch());
        addNavIcon(nav, XUi.IconView.BELL, selected == SCREEN_NOTIFICATIONS, () -> renderNotifications());
        addNavIcon(nav, XUi.IconView.MAIL, selected == SCREEN_MESSAGES, () -> renderMessages());
        addNavIcon(nav, XUi.IconView.PROFILE, selected == SCREEN_PROFILE, () -> renderProfile(currentAccountId));

        return nav;
    }

    private void addNavIcon(LinearLayout nav, int type, boolean active, Runnable action) {
        FrameLayout slot = new FrameLayout(this);
        slot.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        XUi.IconView iv = new XUi.IconView(this, type, pal.fg);
        iv.setActive(active);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER);
        iv.setLayoutParams(ip);
        slot.addView(iv);

        if (type == XUi.IconView.BELL && db.unreadNotifications(currentAccountId) > 0) {
            TextView dot = new TextView(this);
            dot.setBackground(XUi.rounded(XUi.BLUE, 999, this));
            FrameLayout.LayoutParams dpv = new FrameLayout.LayoutParams(dp(8), dp(8));
            dpv.gravity = Gravity.CENTER;
            dpv.leftMargin = dp(18);
            dpv.bottomMargin = dp(16);
            dot.setLayoutParams(dpv);
            slot.addView(dot);
        }
        slot.setOnClickListener(v -> action.run());
        nav.addView(slot);
    }

    private void addComposeFab(FrameLayout frame) {
        FrameLayout fab = new FrameLayout(this);
        fab.setBackground(XUi.rounded(XUi.BLUE, 999, this));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.RIGHT);
        fp.setMargins(0, 0, dp(18), dp(76));
        fab.setLayoutParams(fp);
        fab.setElevation(dp(8));
        XUi.IconView composeIcon = new XUi.IconView(this, XUi.IconView.COMPOSE, Color.WHITE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER);
        composeIcon.setLayoutParams(pp);
        composeIcon.setPadding(dp(2), dp(2), dp(2), dp(2));
        fab.addView(composeIcon);
        fab.setOnClickListener(v -> {
            composeDraft = "";
            composeMediaPath = null;
            composeAuthorId = currentAccountId;
            showComposer(null, null);
        });
        frame.addView(fab);
    }

    private ScrollView scrollOf(LinearLayout body) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(pal.bg);
        s.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private void renderHome() {
        currentScreen = SCREEN_HOME;
        currentProfileId = -1;
        currentPostId = -1;
        clearAccountCache();

        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);

        shell.addView(xLogoTopBar());
        LinearLayout tabs = hbox();
        tabs.addView(tab("For you", homeForYou, v -> { homeForYou = true; renderHome(); }));
        tabs.addView(tab("Following", !homeForYou, v -> { homeForYou = false; renderHome(); }));
        shell.addView(tabs);
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout feed = vbox();
        // Both home tabs are chronological: newest timeline event first.
        // Reposts are timeline events too, so the reposter label survives outside profiles.
        List<Post> posts = db.timelinePosts(currentAccountId, !homeForYou, 250);
        if (posts.isEmpty()) {
            feed.addView(emptyState("Your timeline is quiet", "Follow local accounts or create posts from any identity."));
        } else {
            for (Post p : posts) {
                db.addView(currentAccountId, p.id);
                feed.addView(postView(p, false));
                feed.addView(XUi.divider(this, pal.border));
            }
        }
        ScrollView scroll = scrollOf(feed);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(XUi.divider(this, pal.border));
        shell.addView(bottomNav(SCREEN_HOME));
        addComposeFab(frame);
        setScreen(frame);
    }

    private List<Post> forYouPosts() {
        List<Post> all = db.recentVisiblePosts(currentAccountId, 300);
        ArrayList<Post> top = new ArrayList<>();
        for (Post p : all) if (p.replyTo == null) top.add(p);
        final long now = System.currentTimeMillis();
        Collections.sort(top, (a, b) -> Double.compare(scorePost(b, now), scorePost(a, now)));
        return top;
    }

    private double scorePost(Post p, long now) {
        double hours = Math.max(0, (now - p.createdAt) / 3600000.0);
        double freshness = 600.0 / (1.0 + hours / 5.0);
        double engagement = Math.log10(p.views + 10) * 55.0 + p.likes * 1.7 + p.reposts * 4.5 + p.replies * 2.2;
        double relation = db.isFollowing(currentAccountId, p.authorId) ? 260 : 0;
        if (p.authorId == currentAccountId) relation += 90;
        return p.viralBoost + freshness + engagement + relation;
    }

    private View emptyState(String title, String body) {
        LinearLayout box = vbox();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(36), dp(70), dp(36), dp(70));
        TextView a = tv(title, 28, pal.fg, true);
        a.setGravity(Gravity.CENTER);
        box.addView(a);
        TextView b = tv(body, 15, pal.secondary, false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(10), 0, 0);
        box.addView(b);
        return box;
    }

    private View postView(Post p, boolean detail) {
        Account a = account(p.authorId);
        if (a == null || !db.canSeeAccount(currentAccountId, a.id)) return new View(this);

        LinearLayout row = hbox();
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(12), dp(detail ? 14 : 10), dp(10), dp(10));
        row.setBackgroundColor(pal.bg);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (!detail) renderPost(p.id);
        });
        row.setOnLongClickListener(v -> {
            showDirectorMenu(p.id);
            return true;
        });

        XUi.AvatarView av = new XUi.AvatarView(this, a);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(detail ? 46 : 42), dp(detail ? 46 : 42));
        avp.setMargins(0, 0, dp(10), 0);
        av.setLayoutParams(avp);
        av.setOnClickListener(v -> renderProfile(a.id));
        row.addView(av);

        LinearLayout content = vbox();
        content.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (p.replyTo != null) {
            Post parent = db.getPost(p.replyTo);
            if (parent != null) {
                Account pa = account(parent.authorId);
                if (pa != null && db.canSeeAccount(currentAccountId, pa.id)) {
                    TextView replying = tv("Replying to @" + pa.handle, 14, pal.secondary, false);
                    replying.setPadding(0, 0, 0, dp(3));
                    content.addView(replying);
                }
            }
        }

        LinearLayout meta = hbox();
        TextView name = tv(a.name, detail ? 16 : 15, pal.fg, true);
        name.setMaxLines(1);
        name.setOnClickListener(v -> renderProfile(a.id));
        meta.addView(name);

        if (a.verified) meta.addView(verifiedBadge(17));

        TextView handle = tv(" @" + a.handle + " · " + timeAgo(p.createdAt), 15, pal.secondary, false);
        handle.setMaxLines(1);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        handle.setLayoutParams(hp);
        meta.addView(handle);

        XUi.IconView more = new XUi.IconView(this, XUi.IconView.MORE, pal.secondary);
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(25)));
        more.setPadding(dp(6), dp(5), dp(6), dp(5));
        more.setOnClickListener(v -> showPostMenu(p.id));
        meta.addView(more);
        content.addView(meta);

        if (!p.body.isEmpty()) {
            TextView body = tv("", detail ? 20 : 15, pal.fg, false);
            applyMentionLinks(body, p.body);
            body.setTextIsSelectable(false);
            body.setLineSpacing(0, 1.08f);
            body.setPadding(0, dp(2), dp(4), dp(7));
            content.addView(body);
        }

        if (p.mediaPath != null && new File(p.mediaPath).exists()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(decodeScaled(p.mediaPath, 1200, 900));
            image.setBackground(XUi.rounded(pal.surface, 14, this));
            image.setClipToOutline(true);
            image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(detail ? 330 : 260));
            ip.setMargins(0, dp(4), dp(4), dp(7));
            image.setLayoutParams(ip);
            content.addView(image);
        }

        if (p.quoteOf != null) {
            Post q = db.getPost(p.quoteOf);
            if (q != null) content.addView(quotedPost(q));
        }

        content.addView(actionRow(p));
        row.addView(content);

        if (p.profileRepost && p.profileActorId > 0) {
            Account reposter = account(p.profileActorId);
            LinearLayout outer = vbox();
            LinearLayout repostHeader = hbox();
            repostHeader.setPadding(dp(52), dp(7), dp(12), 0);
            XUi.IconView repostIcon = new XUi.IconView(this, XUi.IconView.REPOST, pal.secondary);
            repostIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
            repostIcon.setPadding(dp(2), dp(2), dp(2), dp(2));
            repostHeader.addView(repostIcon);
            String who = reposter == null ? "Reposted" : reposter.name + " reposted";
            TextView label = tv("  " + who, 13, pal.secondary, true);
            repostHeader.addView(label);
            outer.addView(repostHeader);
            outer.addView(row);
            return outer;
        }
        return row;
    }

    private View quotedPost(Post q) {
        Account qa = account(q.authorId);
        LinearLayout card = vbox();
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackground(XUi.stroked(Color.TRANSPARENT, pal.border, 14, this));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(5), dp(4), dp(8));
        card.setLayoutParams(cp);
        if (qa == null || !db.canSeeAccount(currentAccountId, qa.id)) {
            TextView unavailable = tv("This post is unavailable", 14, pal.secondary, false);
            unavailable.setPadding(0, dp(2), 0, dp(2));
            card.addView(unavailable);
            return card;
        }
        if (qa != null) {
            LinearLayout meta = hbox();
            TextView n = tv(qa.name, 14, pal.fg, true);
            meta.addView(n);
            if (qa.verified) meta.addView(verifiedBadge(15));
            TextView h = tv(" @" + qa.handle + " · " + timeAgo(q.createdAt), 14, pal.secondary, false);
            meta.addView(h);
            card.addView(meta);
        }
        TextView body = tv("", 14, pal.fg, false);
        applyMentionLinks(body, q.body);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);
        card.setOnClickListener(v -> renderPost(q.id));
        return card;
    }

    private LinearLayout actionRow(Post p) {
        LinearLayout actions = hbox();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(3), 0, 0);
        actions.addView(actionItem(XUi.IconView.REPLY, p.replies, pal.secondary, false, v -> {
            composeDraft = "";
            composeMediaPath = null;
            composeAuthorId = currentAccountId;
            showComposer(p.id, null);
        }));
        boolean reposted = db.hasInteraction(currentAccountId, p.id, "repost");
        actions.addView(actionItem(XUi.IconView.REPOST, p.reposts, reposted ? XUi.GREEN : pal.secondary, reposted, v -> {
            db.toggleInteraction(currentAccountId, p.id, "repost");
            refreshCurrent();
        }));
        boolean liked = db.hasInteraction(currentAccountId, p.id, "like");
        actions.addView(actionItem(XUi.IconView.HEART, p.likes, liked ? XUi.PINK : pal.secondary, liked, v -> {
            db.toggleInteraction(currentAccountId, p.id, "like");
            refreshCurrent();
        }));
        actions.addView(actionItem(XUi.IconView.VIEWS, p.views, pal.secondary, false, v -> renderPost(p.id)));
        boolean bookmarked = db.hasInteraction(currentAccountId, p.id, "bookmark");
        actions.addView(actionItem(XUi.IconView.BOOKMARK, -1, bookmarked ? XUi.BLUE : pal.secondary, bookmarked, v -> {
            db.toggleInteraction(currentAccountId, p.id, "bookmark");
            refreshCurrent();
        }));
        actions.addView(actionItem(XUi.IconView.SHARE, -1, pal.secondary, false, v -> showShareMenu(p.id)));
        return actions;
    }

    private View actionItem(int icon, long count, int color, boolean active, View.OnClickListener click) {
        LinearLayout item = hbox();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, dp(32), icon == XUi.IconView.SHARE || icon == XUi.IconView.BOOKMARK ? .7f : 1f));
        XUi.IconView iv = new XUi.IconView(this, icon, color);
        iv.setActive(active);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        iv.setPadding(dp(5), dp(5), dp(5), dp(5));
        item.addView(iv);
        if (count >= 0) {
            TextView c = tv(formatCount(count), 12, color, false);
            item.addView(c);
        }
        item.setOnClickListener(click);
        return item;
    }

    private void renderPost(long postId) {
        Post p = db.getPost(postId);
        if (p == null || !db.canSeeAccount(currentAccountId, p.authorId)) {
            Toast.makeText(this, "This post isn't available from this account", Toast.LENGTH_SHORT).show();
            renderHome();
            return;
        }
        currentScreen = SCREEN_POST;
        currentPostId = postId;

        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);

        shell.addView(topBar("Post", true));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout body = vbox();
        db.addView(currentAccountId, p.id);
        body.addView(postView(p, true));
        body.addView(XUi.divider(this, pal.border));

        List<Post> replies = db.repliesTo(p.id, currentAccountId);
        for (Post r : replies) {
            db.addView(currentAccountId, r.id);
            body.addView(postView(r, false));
            body.addView(XUi.divider(this, pal.border));
        }

        ScrollView scroll = scrollOf(body);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);

        LinearLayout reply = hbox();
        reply.setGravity(Gravity.CENTER_VERTICAL);
        reply.setPadding(dp(12), dp(8), dp(12), dp(8));
        Account me = account(currentAccountId);
        XUi.AvatarView av = new XUi.AvatarView(this, me);
        LinearLayout.LayoutParams replyAvatar = new LinearLayout.LayoutParams(dp(42), dp(42));
        replyAvatar.setMargins(0, 0, dp(10), 0);
        av.setLayoutParams(replyAvatar);
        reply.addView(av);
        TextView prompt = tv("Post your reply", 15, pal.secondary, false);
        prompt.setGravity(Gravity.CENTER_VERTICAL);
        prompt.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        reply.addView(prompt);
        reply.setOnClickListener(v -> {
            composeDraft = "";
            composeMediaPath = null;
            composeAuthorId = currentAccountId;
            showComposer(p.id, null);
        });
        shell.addView(XUi.divider(this, pal.border));
        shell.addView(reply);
        shell.addView(bottomNav(0));
        setScreen(frame);
    }

    private void renderProfile(long accountId) {
        Account a = db.getAccount(accountId);
        if (a == null) return;
        if (!db.canSeeAccount(currentAccountId, accountId)) {
            Toast.makeText(this, "This account isn't available from the current account", Toast.LENGTH_SHORT).show();
            renderHome();
            return;
        }
        currentScreen = SCREEN_PROFILE;
        currentProfileId = accountId;
        clearAccountCache();

        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);

        LinearLayout bar = topBar(a.name, true);
        TextView count = tv(formatCount(db.postsByAccount(accountId, true).size()) + " posts", 12, pal.secondary, false);
        count.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        count.setPadding(dp(8), 0, dp(8), 0);
        bar.addView(count);
        shell.addView(bar);

        LinearLayout body = vbox();
        body.setOnLongClickListener(v -> {
            showEditAccount(accountId);
            return true;
        });

        FrameLayout hero = new FrameLayout(this);
        hero.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(206)));
        ImageView banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setBackgroundColor(pal.surface);
        if (a.bannerPath != null && new File(a.bannerPath).exists()) {
            banner.setImageBitmap(decodeScaled(a.bannerPath, 1600, 500));
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        banner.setLayoutParams(bp);
        hero.addView(banner);

        XUi.AvatarView avatar = new XUi.AvatarView(this, a);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(88), dp(88));
        ap.leftMargin = dp(14);
        ap.topMargin = dp(108);
        avatar.setLayoutParams(ap);
        avatar.setBackground(XUi.stroked(pal.bg, pal.bg, 999, this));
        avatar.setOnLongClickListener(v -> {
            showEditAccount(accountId);
            return true;
        });
        hero.addView(avatar);

        TextView action;
        if (accountId == currentAccountId) {
            action = pill("Edit profile", false);
            action.setOnClickListener(v -> showEditAccount(accountId));
        } else {
            boolean following = db.isFollowing(currentAccountId, accountId);
            action = pill(following ? "Following" : "Follow", !following);
            action.setOnClickListener(v -> {
                db.toggleFollow(currentAccountId, accountId);
                renderProfile(accountId);
            });
        }
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36), Gravity.RIGHT | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(14), dp(13));
        action.setLayoutParams(fp);
        hero.addView(action);
        body.addView(hero);

        LinearLayout info = vbox();
        info.setPadding(dp(14), 0, dp(14), dp(14));
        LinearLayout nameRow = hbox();
        nameRow.addView(tv(a.name, 21, pal.fg, true));
        if (a.verified) nameRow.addView(verifiedBadge(19));
        body.addView(info);
        info.addView(nameRow);
        info.addView(tv("@" + a.handle, 15, pal.secondary, false));

        if (a.bio != null && !a.bio.isEmpty()) {
            TextView bio = tv(a.bio, 15, pal.fg, false);
            bio.setPadding(0, dp(11), 0, dp(10));
            info.addView(bio);
        }

        LinearLayout metadata = vbox();
        metadata.setPadding(0, dp(1), 0, dp(8));

        if (a.location != null && !a.location.trim().isEmpty()) {
            metadata.addView(profileMetaRow(XUi.IconView.LOCATION, a.location.trim(), pal.secondary));
        }
        if (a.website != null && !a.website.trim().isEmpty()) {
            metadata.addView(profileMetaRow(XUi.IconView.LINK, a.website.trim(), XUi.BLUE));
        }
        if (a.birthDate != null && !a.birthDate.trim().isEmpty()) {
            metadata.addView(profileMetaRow(XUi.IconView.BALLOON, "Born " + a.birthDate.trim(), pal.secondary));
        }
        long joinedAt = a.createdAt > 0 ? a.createdAt : System.currentTimeMillis();
        String joinedText = "Joined " + new SimpleDateFormat("MMMM yyyy", Locale.US).format(new Date(joinedAt));
        metadata.addView(profileMetaRow(XUi.IconView.CALENDAR, joinedText, pal.secondary));
        info.addView(metadata);

        LinearLayout stats = hbox();
        long following = a.displayFollowing >= 0 ? a.displayFollowing : db.actualFollowing(a.id);
        long followers = a.displayFollowers >= 0 ? a.displayFollowers : db.actualFollowers(a.id);

        LinearLayout followingGroup = hbox();
        followingGroup.addView(tv(formatCount(following) + " ", 14, pal.fg, true));
        followingGroup.addView(tv("Following", 14, pal.secondary, false));
        if (accountId == currentAccountId) {
            followingGroup.setClickable(true);
            followingGroup.setOnClickListener(v -> renderFollowList(accountId, true));
        }
        stats.addView(followingGroup);
        stats.addView(space(18, 1));

        LinearLayout followerGroup = hbox();
        followerGroup.addView(tv(formatCount(followers) + " ", 14, pal.fg, true));
        followerGroup.addView(tv("Followers", 14, pal.secondary, false));
        if (accountId == currentAccountId) {
            followerGroup.setClickable(true);
            followerGroup.setOnClickListener(v -> renderFollowList(accountId, false));
        }
        stats.addView(followerGroup);
        info.addView(stats);

        LinearLayout tabs = hbox();
        String[] names = {"Posts", "Replies", "Media", "Likes"};
        for (int i = 0; i < names.length; i++) {
            final int ix = i;
            tabs.addView(tab(names[i], profileTab == i, v -> {
                profileTab = ix;
                renderProfile(accountId);
            }));
        }
        body.addView(tabs);
        body.addView(XUi.divider(this, pal.border));

        List<Post> posts;
        if (profileTab == 0) posts = db.profileTimeline(accountId, currentAccountId);
        else if (profileTab == 1) posts = db.postsByAccount(accountId, true);
        else if (profileTab == 3) posts = db.likedPosts(accountId, currentAccountId);
        else {
            posts = new ArrayList<>();
            for (Post p : db.postsByAccount(accountId, true)) if (p.mediaPath != null) posts.add(p);
        }

        if (posts.isEmpty()) {
            body.addView(emptyState(profileTab == 2 ? "No media yet" : "Nothing here yet", "This is a local profile, so you decide what appears."));
        } else {
            for (Post p : posts) {
                body.addView(postView(p, false));
                body.addView(XUi.divider(this, pal.border));
            }
        }

        ScrollView scroll = scrollOf(body);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(accountId == currentAccountId ? SCREEN_PROFILE : 0));
        addComposeFab(frame);
        setScreen(frame);
    }

    private View profileMetaRow(int iconType, String text, int textColor) {
        LinearLayout row = hbox();
        row.setPadding(0, dp(3), 0, dp(3));
        XUi.IconView icon = new XUi.IconView(this, iconType, pal.secondary);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(19), dp(19));
        ip.setMargins(0, 0, dp(5), 0);
        icon.setLayoutParams(ip);
        icon.setPadding(dp(1), dp(1), dp(1), dp(1));
        row.addView(icon);
        TextView label = tv(text, 15, textColor, false);
        row.addView(label);
        return row;
    }

    private void renderFollowList(long accountId, boolean followingList) {
        if (accountId != currentAccountId) {
            Toast.makeText(this, "Follow lists are available for the account you're currently using", Toast.LENGTH_SHORT).show();
            return;
        }
        currentScreen = SCREEN_FOLLOW_LIST;
        currentProfileId = accountId;
        currentFollowListFollowing = followingList;

        LinearLayout shell = vbox();
        shell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(topBar(followingList ? "Following" : "Followers", true));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout list = vbox();
        List<Account> accounts = followingList ? db.followingAccounts(accountId) : db.followerAccounts(accountId);
        int visible = 0;
        for (Account item : accounts) {
            if (!db.canSeeAccount(currentAccountId, item.id)) continue;
            visible++;
            list.addView(accountRow(item));
            list.addView(XUi.divider(this, pal.border));
        }
        if (visible == 0) {
            list.addView(emptyState(followingList ? "Not following anyone yet" : "No followers yet",
                    "This list reflects the real local follow graph, not the displayed fake count."));
        }

        ScrollView scroll = scrollOf(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(0));
        setScreen(shell);
    }

    private void renderSearch() {
        currentScreen = SCREEN_SEARCH;
        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);

        LinearLayout top = hbox();
        top.setPadding(dp(12), dp(7), dp(12), dp(7));
        top.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        Account me = account(currentAccountId);
        XUi.AvatarView av = new XUi.AvatarView(this, me);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(32), dp(32));
        ap.setMargins(0, 0, dp(10), 0);
        av.setLayoutParams(ap);
        av.setOnClickListener(v -> showAccountSwitcher());
        top.addView(av);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(pal.fg);
        search.setHintTextColor(pal.secondary);
        search.setHint("Search");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(XUi.rounded(pal.surface, 999, this));
        search.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        top.addView(search);
        shell.addView(top);
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout results = vbox();
        ScrollView scroll = scrollOf(results);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(XUi.divider(this, pal.border));
        shell.addView(bottomNav(SCREEN_SEARCH));

        Runnable update = () -> populateSearch(results, search.getText().toString().trim());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { update.run(); }
            public void afterTextChanged(Editable e) {}
        });
        update.run();
        setScreen(frame);
    }

    private void populateSearch(LinearLayout results, String q) {
        results.removeAllViews();
        if (q.isEmpty()) {
            TextView h = tv("Explore your universe", 22, pal.fg, true);
            h.setPadding(dp(16), dp(22), dp(16), dp(8));
            results.addView(h);
            for (Account a : db.listVisibleAccounts(currentAccountId)) results.addView(accountRow(a));
            return;
        }

        List<Account> accounts = db.searchAccounts(q, currentAccountId);
        if (!accounts.isEmpty()) {
            TextView h = tv("People", 20, pal.fg, true);
            h.setPadding(dp(16), dp(14), dp(16), dp(7));
            results.addView(h);
            for (Account a : accounts) results.addView(accountRow(a));
            results.addView(XUi.divider(this, pal.border));
        }

        List<Post> posts = db.searchPosts(q, currentAccountId);
        if (!posts.isEmpty()) {
            TextView h = tv("Posts", 20, pal.fg, true);
            h.setPadding(dp(16), dp(14), dp(16), dp(7));
            results.addView(h);
            for (Post p : posts) {
                results.addView(postView(p, false));
                results.addView(XUi.divider(this, pal.border));
            }
        }

        if (accounts.isEmpty() && posts.isEmpty()) results.addView(emptyState("No results", "Try another local name, handle or phrase."));
    }

    private View accountRow(Account a) {
        LinearLayout row = hbox();
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        XUi.AvatarView av = new XUi.AvatarView(this, a);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(45), dp(45));
        ap.setMargins(0, 0, dp(11), 0);
        av.setLayoutParams(ap);
        row.addView(av);

        LinearLayout labels = vbox();
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout nrow = hbox();
        nrow.addView(tv(a.name, 15, pal.fg, true));
        if (a.verified) nrow.addView(verifiedBadge(16));
        labels.addView(nrow);
        labels.addView(tv("@" + a.handle, 14, pal.secondary, false));
        if (a.bio != null && !a.bio.isEmpty()) {
            TextView bio = tv(a.bio, 14, pal.fg, false);
            bio.setPadding(0, dp(3), 0, 0);
            labels.addView(bio);
        }
        row.addView(labels);
        row.setOnClickListener(v -> renderProfile(a.id));
        row.setOnLongClickListener(v -> { showEditAccount(a.id); return true; });
        return row;
    }

    private void renderNotifications() {
        currentScreen = SCREEN_NOTIFICATIONS;
        db.markNotificationsRead(currentAccountId);

        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);
        shell.addView(topBar("Notifications", false));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout list = vbox();
        List<LocalNotification> ns = db.notifications(currentAccountId);
        if (ns.isEmpty()) {
            list.addView(emptyState("Nothing to see here — yet", "Likes, reposts, follows and replies from your local accounts will appear here."));
        } else {
            for (LocalNotification n : ns) {
                list.addView(notificationRow(n));
                list.addView(XUi.divider(this, pal.border));
            }
        }
        ScrollView scroll = scrollOf(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(SCREEN_NOTIFICATIONS));
        setScreen(frame);
    }

    private View notificationRow(LocalNotification n) {
        Account actor = account(n.actorId);
        LinearLayout row = hbox();
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(18), dp(12), dp(14), dp(12));
        XUi.IconView kind = new XUi.IconView(this,
                "follow".equals(n.type) ? XUi.IconView.PLUS :
                        ("like".equals(n.type) ? XUi.IconView.HEART :
                                ("repost".equals(n.type) ? XUi.IconView.REPOST : XUi.IconView.REPLY)),
                "like".equals(n.type) ? XUi.PINK : ("repost".equals(n.type) ? XUi.GREEN : XUi.BLUE));
        kind.setLayoutParams(new LinearLayout.LayoutParams(dp(31), dp(31)));
        kind.setPadding(dp(5), dp(5), dp(5), dp(5));
        row.addView(kind);
        LinearLayout content = vbox();
        content.setPadding(dp(8), 0, 0, 0);
        content.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (actor != null) {
            XUi.AvatarView av = new XUi.AvatarView(this, actor);
            av.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
            content.addView(av);
            String verb = "follow".equals(n.type) ? " followed you" :
                    ("like".equals(n.type) ? " liked your post" :
                            ("repost".equals(n.type) ? " reposted your post" : " replied to your post"));
            TextView line = tv(actor.name + verb, 15, pal.fg, false);
            line.setPadding(0, dp(7), 0, 0);
            content.addView(line);
        }
        if (n.postId != null) {
            Post p = db.getPost(n.postId);
            if (p != null) {
                TextView excerpt = tv(p.body, 14, pal.secondary, false);
                excerpt.setMaxLines(3);
                excerpt.setPadding(0, dp(4), 0, 0);
                content.addView(excerpt);
            }
        }
        row.addView(content);
        if (n.postId != null) row.setOnClickListener(v -> renderPost(n.postId));
        else if (actor != null) row.setOnClickListener(v -> renderProfile(actor.id));
        return row;
    }

    private void renderMessages() {
        currentScreen = SCREEN_MESSAGES;
        FrameLayout frame = baseFrame();
        LinearLayout shell = vbox();
        shell.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(shell);
        shell.addView(topBar("Messages", false));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout list = vbox();
        List<Account> accounts = db.listVisibleAccounts(currentAccountId);
        Collections.sort(accounts, (a, b) -> Boolean.compare(db.hasMessages(currentAccountId, b.id), db.hasMessages(currentAccountId, a.id)));
        boolean any = false;
        for (Account a : accounts) {
            if (a.id == currentAccountId) continue;
            any = true;
            LinearLayout row = (LinearLayout) accountRow(a);
            row.setOnClickListener(v -> renderChat(a.id));
            list.addView(row);
            list.addView(XUi.divider(this, pal.border));
        }
        if (!any) list.addView(emptyState("No one else is here", "Create another local account to start a private conversation."));

        ScrollView scroll = scrollOf(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(SCREEN_MESSAGES));
        setScreen(frame);
    }

    private void renderChat(long otherId) {
        Account other = db.getAccount(otherId);
        if (other == null) return;
        if (!db.canSeeAccount(currentAccountId, otherId)) {
            Toast.makeText(this, "This account isn't available from the current account", Toast.LENGTH_SHORT).show();
            renderMessages();
            return;
        }
        currentScreen = SCREEN_CHAT;
        currentChatId = otherId;

        LinearLayout root = vbox();
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = topBar(other.name, true);
        XUi.AvatarView av = new XUi.AvatarView(this, other);
        av.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        bar.addView(av);
        root.addView(bar);
        root.addView(XUi.divider(this, pal.border));

        LinearLayout messages = vbox();
        messages.setPadding(dp(12), dp(12), dp(12), dp(12));
        List<DirectMessage> convo = db.conversation(currentAccountId, otherId);
        if (convo.isEmpty()) {
            messages.addView(emptyState("Start a conversation", "Messages are stored only on this device."));
        } else {
            for (DirectMessage m : convo) messages.addView(messageBubble(m));
        }
        ScrollView scroll = scrollOf(messages);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(scroll);

        root.addView(XUi.divider(this, pal.border));
        LinearLayout composer = hbox();
        composer.setPadding(dp(10), dp(8), dp(10), dp(8));
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setTextSize(15);
        input.setTextColor(pal.fg);
        input.setHintTextColor(pal.secondary);
        input.setHint("Start a message");
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setBackground(XUi.stroked(pal.surface, pal.border, 18, this));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        composer.addView(input);
        TextView send = pill("Send", true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        sp.setMargins(dp(8), 0, 0, 0);
        send.setLayoutParams(sp);
        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (!body.isEmpty()) {
                db.sendMessage(currentAccountId, otherId, body);
                renderChat(otherId);
            }
        });
        composer.addView(send);
        root.addView(composer);
        setScreen(root);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View messageBubble(DirectMessage m) {
        boolean mine = m.senderId == currentAccountId;
        LinearLayout line = hbox();
        line.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        line.setPadding(0, dp(4), 0, dp(4));
        TextView bubble = tv(m.body, 15, mine ? Color.WHITE : pal.fg, false);
        bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
        bubble.setBackground(XUi.rounded(mine ? XUi.BLUE : pal.surface, 18, this));
        bubble.setMaxWidth(dp(290));
        if (mine) {
            bubble.setOnLongClickListener(v -> {
                showMessageMenu(m);
                return true;
            });
        }
        line.addView(bubble);
        return line;
    }

    private void showMessageMenu(DirectMessage m) {
        String[] options = {"Edit message", "Delete message"};
        new AlertDialog.Builder(this)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        EditText edit = field("Message", true);
                        edit.setText(m.body);
                        new AlertDialog.Builder(this)
                                .setTitle("Edit message")
                                .setView(edit)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Save", (x,w) -> {
                                    String value = edit.getText().toString().trim();
                                    if (!value.isEmpty()) {
                                        db.updateMessage(m.id, currentAccountId, value);
                                        renderChat(currentChatId);
                                    }
                                }).show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete message?")
                                .setMessage("This removes the local message from this simulated conversation.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (x,w) -> {
                                    db.deleteMessage(m.id, currentAccountId);
                                    renderChat(currentChatId);
                                }).show();
                    }
                }).show();
    }

    private void renderBookmarks() {
        currentScreen = SCREEN_BOOKMARKS;
        LinearLayout shell = vbox();
        shell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(topBar("Bookmarks", true));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout list = vbox();
        List<Post> all = db.recentVisiblePosts(currentAccountId, 500);
        int count = 0;
        for (Post p : all) {
            if (db.hasInteraction(currentAccountId, p.id, "bookmark")) {
                count++;
                list.addView(postView(p, false));
                list.addView(XUi.divider(this, pal.border));
            }
        }
        if (count == 0) list.addView(emptyState("Save posts for later", "Bookmarked posts from this local account will show up here."));
        ScrollView scroll = scrollOf(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(0));
        setScreen(shell);
    }

    private void showComposer(Long replyTo, Long quoteOf) {
        composeReplyTo = replyTo;
        composeQuoteOf = quoteOf;
        if (composeAuthorId <= 0 || db.getAccount(composeAuthorId) == null) composeAuthorId = currentAccountId;

        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        LinearLayout root = vbox();
        root.setPadding(0, dp(4), 0, 0);

        LinearLayout top = hbox();
        top.setPadding(dp(10), dp(5), dp(10), dp(5));
        top.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        XUi.IconView close = new XUi.IconView(this, XUi.IconView.CLOSE, pal.fg);
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        top.addView(close);

        Space flex = new Space(this);
        flex.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(flex);

        TextView draftsButton = tv("Drafts", 15, XUi.BLUE, true);
        draftsButton.setGravity(Gravity.CENTER);
        draftsButton.setPadding(dp(12), 0, dp(12), 0);
        draftsButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        top.addView(draftsButton);

        TextView postButton = tv(replyTo == null ? "Post" : "Reply", 15, Color.WHITE, true);
        postButton.setGravity(Gravity.CENTER);
        postButton.setPadding(dp(18), 0, dp(18), 0);
        postButton.setBackground(XUi.rounded(XUi.BLUE, 999, this));
        postButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        top.addView(postButton);
        root.addView(top);

        LinearLayout scrollBody = vbox();
        scrollBody.setPadding(dp(16), dp(10), dp(12), dp(12));

        Account author = account(composeAuthorId);
        LinearLayout editorLine = hbox();
        editorLine.setGravity(Gravity.TOP);

        XUi.AvatarView av = new XUi.AvatarView(this, author);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(46), dp(46));
        avp.setMargins(0, 0, dp(10), 0);
        av.setLayoutParams(avp);
        av.setOnClickListener(v -> {
            EditText body = root.findViewWithTag("composer_body");
            if (body != null) composeDraft = body.getText().toString();
            d.dismiss();
            chooseComposerAuthor(replyTo, quoteOf);
        });
        editorLine.addView(av);

        LinearLayout editorColumn = vbox();
        editorColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (replyTo != null) {
            Post parent = db.getPost(replyTo);
            Account pa = parent == null ? null : account(parent.authorId);
            if (pa != null && db.canSeeAccount(currentAccountId, pa.id)) {
                TextView replying = tv("Replying to @" + pa.handle, 14, pal.secondary, false);
                replying.setPadding(0, 0, 0, dp(6));
                editorColumn.addView(replying);
            }
        }

        EditText body = new EditText(this);
        body.setTag("composer_body");
        body.setTextSize(21);
        body.setTextColor(pal.fg);
        body.setHintTextColor(pal.secondary);
        body.setHint(replyTo == null ? "What is happening?" : "Post your reply");
        body.setGravity(Gravity.TOP | Gravity.LEFT);
        body.setBackgroundColor(Color.TRANSPARENT);
        body.setPadding(0, 0, dp(6), dp(8));
        body.setMinHeight(dp(190));
        body.setMaxLines(30);
        editorColumn.addView(body);
        editorLine.addView(editorColumn);
        scrollBody.addView(editorLine);

        LinearLayout mentionResults = vbox();
        ScrollView mentionScroll = scrollOf(mentionResults);
        LinearLayout.LayoutParams msp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        msp.setMargins(dp(56), 0, dp(4), dp(8));
        mentionScroll.setLayoutParams(msp);
        mentionScroll.setBackground(XUi.stroked(pal.surface, pal.border, 12, this));
        mentionScroll.setVisibility(View.GONE);
        scrollBody.addView(mentionScroll);
        wireMentionAutocomplete(body, mentionResults, mentionScroll);
        body.setText(composeDraft == null ? "" : composeDraft);
        body.setSelection(body.getText().length());

        if (composeMediaPath != null && new File(composeMediaPath).exists()) {
            FrameLayout mediaWrap = new FrameLayout(this);
            LinearLayout.LayoutParams mwp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
            mwp.setMargins(dp(56), dp(4), dp(4), dp(10));
            mediaWrap.setLayoutParams(mwp);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(decodeScaled(composeMediaPath, 1200, 900));
            image.setBackground(XUi.rounded(pal.surface, 16, this));
            image.setClipToOutline(true);
            image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            image.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mediaWrap.addView(image);

            XUi.IconView remove = new XUi.IconView(this, XUi.IconView.CLOSE, Color.WHITE);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.RIGHT | Gravity.TOP);
            rp.setMargins(0, dp(8), dp(8), 0);
            remove.setLayoutParams(rp);
            remove.setPadding(dp(8), dp(8), dp(8), dp(8));
            remove.setBackground(XUi.rounded(0xaa000000, 999, this));
            remove.setOnClickListener(v -> {
                composeDraft = body.getText().toString();
                composeMediaPath = null;
                d.dismiss();
                showComposer(replyTo, quoteOf);
            });
            mediaWrap.addView(remove);
            scrollBody.addView(mediaWrap);
        }

        if (quoteOf != null) {
            Post q = db.getPost(quoteOf);
            if (q != null) {
                View qv = quotedPost(q);
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                qp.setMargins(dp(56), 0, dp(4), dp(10));
                qv.setLayoutParams(qp);
                scrollBody.addView(qv);
            }
        }

        ScrollView contentScroll = scrollOf(scrollBody);
        contentScroll.setFillViewport(false);
        contentScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(contentScroll);

        root.addView(XUi.divider(this, pal.border));

        LinearLayout permission = hbox();
        permission.setPadding(dp(18), dp(8), dp(12), dp(8));
        XUi.IconView globe = new XUi.IconView(this, XUi.IconView.GLOBE, XUi.BLUE);
        globe.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        globe.setPadding(dp(2), dp(2), dp(2), dp(2));
        permission.addView(globe);
        TextView permissionText = tv("  Everyone can reply", 14, XUi.BLUE, true);
        permission.addView(permissionText);
        root.addView(permission);
        root.addView(XUi.divider(this, pal.border));

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = hbox();
        tools.setPadding(dp(12), dp(3), dp(8), dp(3));

        XUi.IconView photo = composerTool(XUi.IconView.PHOTO);
        photo.setOnClickListener(v -> {
            composeDraft = body.getText().toString();
            d.dismiss();
            pickImage(PICK_POST_MEDIA);
        });
        tools.addView(photo);

        XUi.IconView camera = composerTool(XUi.IconView.CAMERA);
        camera.setOnClickListener(v -> Toast.makeText(this, "Use the image picker to add a camera photo", Toast.LENGTH_SHORT).show());
        tools.addView(camera);

        XUi.IconView gif = composerTool(XUi.IconView.GIF);
        gif.setOnClickListener(v -> Toast.makeText(this, "GIF picker is visual-only in the local simulator", Toast.LENGTH_SHORT).show());
        tools.addView(gif);

        XUi.IconView poll = composerTool(XUi.IconView.POLL);
        poll.setOnClickListener(v -> Toast.makeText(this, "Poll composer isn't implemented yet", Toast.LENGTH_SHORT).show());
        tools.addView(poll);

        XUi.IconView location = composerTool(XUi.IconView.LOCATION);
        location.setOnClickListener(v -> Toast.makeText(this, "Location attachment isn't used by this local simulator", Toast.LENGTH_SHORT).show());
        tools.addView(location);

        XUi.IconView schedule = composerTool(XUi.IconView.SCHEDULE);
        schedule.setOnClickListener(v -> Toast.makeText(this, "Scheduled posts aren't enabled yet", Toast.LENGTH_SHORT).show());
        tools.addView(schedule);

        XUi.IconView plus = composerTool(XUi.IconView.PLUS_CIRCLE);
        plus.setOnClickListener(v -> Toast.makeText(this, "Add another post is not needed for a single local post", Toast.LENGTH_SHORT).show());
        tools.addView(plus);

        toolbarScroll.addView(tools, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)));
        root.addView(toolbarScroll);

        TextWatcher postState = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean ready = hasComposerContent(s == null ? "" : s.toString(), quoteOf);
                postButton.setAlpha(ready ? 1f : .55f);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        body.addTextChangedListener(postState);
        postButton.setAlpha(hasComposerContent(body.getText().toString(), quoteOf) ? 1f : .55f);

        close.setOnClickListener(v -> maybeCloseComposer(d, body, replyTo, quoteOf));

        draftsButton.setOnClickListener(v -> {
            composeDraft = body.getText().toString();
            if (hasComposerContent(composeDraft, quoteOf)) {
                activeDraftId = db.saveDraft(activeDraftId, composeAuthorId, composeDraft, composeMediaPath, replyTo, quoteOf);
            }
            d.dismiss();
            renderDrafts();
        });

        postButton.setOnClickListener(v -> {
            String text = body.getText().toString().trim();
            if (!hasComposerContent(text, quoteOf)) return;
            db.insertPost(composeAuthorId, text, composeMediaPath, replyTo, quoteOf);
            if (activeDraftId > 0) db.deleteDraft(activeDraftId);
            resetComposerState();
            d.dismiss();
            if (replyTo != null) renderPost(replyTo); else renderHome();
        });

        d.setContentView(root);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(pal.bg));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        body.requestFocus();
    }

    private XUi.IconView composerTool(int type) {
        XUi.IconView icon = new XUi.IconView(this, type, XUi.BLUE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(46));
        lp.setMargins(0, 0, dp(3), 0);
        icon.setLayoutParams(lp);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        return icon;
    }

    private boolean hasComposerContent(String text, Long quoteOf) {
        return (text != null && !text.trim().isEmpty())
                || (composeMediaPath != null && !composeMediaPath.isEmpty())
                || quoteOf != null;
    }

    private void maybeCloseComposer(Dialog d, EditText body, Long replyTo, Long quoteOf) {
        composeDraft = body.getText().toString();
        if (!hasComposerContent(composeDraft, quoteOf)) {
            resetComposerState();
            d.dismiss();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Save post?")
                .setMessage("Keep this unfinished post in Drafts?")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Discard", (x,w) -> {
                    if (activeDraftId > 0) db.deleteDraft(activeDraftId);
                    resetComposerState();
                    d.dismiss();
                })
                .setPositiveButton("Save draft", (x,w) -> {
                    db.saveDraft(activeDraftId, composeAuthorId, composeDraft, composeMediaPath, replyTo, quoteOf);
                    resetComposerState();
                    d.dismiss();
                }).show();
    }

    private void resetComposerState() {
        composeDraft = "";
        composeMediaPath = null;
        composeReplyTo = null;
        composeQuoteOf = null;
        composeAuthorId = currentAccountId;
        activeDraftId = -1;
    }

    private void renderDrafts() {
        currentScreen = SCREEN_DRAFTS;
        LinearLayout shell = vbox();
        shell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(topBar("Drafts", true));
        shell.addView(XUi.divider(this, pal.border));

        LinearLayout list = vbox();
        List<DraftPost> drafts = db.drafts(currentAccountId);
        if (drafts.isEmpty()) {
            list.addView(emptyState("No drafts", "Unfinished posts you save will appear here."));
        } else {
            for (DraftPost draft : drafts) {
                LinearLayout row = hbox();
                row.setGravity(Gravity.TOP);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                Account a = account(draft.authorId);
                XUi.AvatarView av = new XUi.AvatarView(this, a);
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(42), dp(42));
                ap.setMargins(0, 0, dp(10), 0);
                av.setLayoutParams(ap);
                row.addView(av);

                LinearLayout text = vbox();
                text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                String preview = draft.body == null || draft.body.trim().isEmpty() ? "(media / quoted post)" : draft.body;
                TextView body = tv(preview, 15, pal.fg, false);
                body.setMaxLines(3);
                text.addView(body);
                TextView date = tv(new SimpleDateFormat("MMM d · h:mm a", Locale.US).format(new Date(draft.createdAt)), 12, pal.secondary, false);
                date.setPadding(0, dp(5), 0, 0);
                text.addView(date);
                row.addView(text);

                row.setOnClickListener(v -> {
                    DraftPost load = db.getDraft(draft.id);
                    if (load == null) return;
                    activeDraftId = load.id;
                    composeAuthorId = load.authorId;
                    composeDraft = load.body == null ? "" : load.body;
                    composeMediaPath = load.mediaPath;
                    composeReplyTo = load.replyTo;
                    composeQuoteOf = load.quoteOf;
                    showComposer(load.replyTo, load.quoteOf);
                });
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete draft?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (x,w) -> {
                                db.deleteDraft(draft.id);
                                renderDrafts();
                            }).show();
                    return true;
                });
                list.addView(row);
                list.addView(XUi.divider(this, pal.border));
            }
        }

        ScrollView scroll = scrollOf(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(scroll);
        shell.addView(bottomNav(0));
        setScreen(shell);
    }

    private void chooseComposerAuthor(Long replyTo, Long quoteOf) {
        List<Account> accounts = db.listAccounts();
        String[] labels = new String[accounts.size()];
        int checked = 0;
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            labels[i] = a.name + "  @" + a.handle;
            if (a.id == composeAuthorId) checked = i;
        }
        final int initial = checked;
        new AlertDialog.Builder(this)
                .setTitle("Post as")
                .setSingleChoiceItems(labels, checked, null)
                .setPositiveButton("Use account", (dialog, which) -> {
                    AlertDialog ad = (AlertDialog) dialog;
                    int pos = ad.getListView().getCheckedItemPosition();
                    if (pos < 0) pos = initial;
                    composeAuthorId = accounts.get(pos).id;
                    showComposer(replyTo, quoteOf);
                })
                .setNegativeButton("Cancel", (dialog, which) -> showComposer(replyTo, quoteOf))
                .show();
    }

    private void showAccountSwitcher() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = vbox();
        root.setPadding(dp(18), dp(26), dp(18), dp(22));

        Account me = account(currentAccountId);
        if (me == null) return;

        LinearLayout avatarRow = hbox();
        XUi.AvatarView avatar = new XUi.AvatarView(this, me);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        avatar.setOnClickListener(v -> { d.dismiss(); renderProfile(currentAccountId); });
        avatarRow.addView(avatar);
        Space aflex = new Space(this);
        aflex.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        avatarRow.addView(aflex);
        XUi.IconView more = new XUi.IconView(this, XUi.IconView.MORE, pal.fg);
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        more.setPadding(dp(8), dp(8), dp(8), dp(8));
        more.setOnClickListener(v -> showEditAccount(currentAccountId));
        avatarRow.addView(more);
        root.addView(avatarRow);

        LinearLayout nameRow = hbox();
        nameRow.setPadding(0, dp(18), 0, 0);
        nameRow.addView(tv(me.name, 23, pal.fg, true));
        if (me.verified) nameRow.addView(verifiedBadge(19));
        root.addView(nameRow);
        TextView handle = tv("@" + me.handle, 15, pal.secondary, false);
        handle.setPadding(0, dp(3), 0, dp(14));
        root.addView(handle);

        LinearLayout stats = hbox();
        long following = me.displayFollowing >= 0 ? me.displayFollowing : db.actualFollowing(me.id);
        long followers = me.displayFollowers >= 0 ? me.displayFollowers : db.actualFollowers(me.id);
        TextView followingText = tv(formatCount(following) + " Following", 15, pal.secondary, false);
        followingText.setPadding(0, 0, dp(18), 0);
        followingText.setOnClickListener(v -> { d.dismiss(); renderFollowList(me.id, true); });
        stats.addView(followingText);
        TextView followerText = tv(formatCount(followers) + " Followers", 15, pal.secondary, false);
        followerText.setOnClickListener(v -> { d.dismiss(); renderFollowList(me.id, false); });
        stats.addView(followerText);
        root.addView(stats);

        View divider = XUi.divider(this, pal.border);
        LinearLayout.LayoutParams divp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divp.setMargins(0, dp(20), 0, dp(9));
        divider.setLayoutParams(divp);
        root.addView(divider);

        root.addView(drawerAction(XUi.IconView.PROFILE, "Profile", () -> { d.dismiss(); renderProfile(currentAccountId); }));
        root.addView(drawerAction(XUi.IconView.BOOKMARK, "Bookmarks", () -> { d.dismiss(); renderBookmarks(); }));
        root.addView(drawerAction(XUi.IconView.DRAFTS, "Drafts", () -> { d.dismiss(); renderDrafts(); }));

        LinearLayout accountsBox = vbox();
        accountsBox.setVisibility(View.GONE);
        TextView switcher = (TextView) drawerActionText("Switch accounts", () -> {
            accountsBox.setVisibility(accountsBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        root.addView(switcher);
        root.addView(accountsBox);

        for (Account a : db.listAccounts()) {
            LinearLayout row = hbox();
            row.setPadding(dp(6), dp(7), dp(4), dp(7));
            XUi.AvatarView av = new XUi.AvatarView(this, a);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(38), dp(38));
            ap.setMargins(0, 0, dp(10), 0);
            av.setLayoutParams(ap);
            row.addView(av);
            LinearLayout labels = vbox();
            labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout nr = hbox();
            nr.addView(tv(a.name, 15, pal.fg, true));
            if (a.verified) nr.addView(verifiedBadge(15));
            labels.addView(nr);
            labels.addView(tv("@" + a.handle, 13, pal.secondary, false));
            row.addView(labels);
            if (a.id == currentAccountId) {
                XUi.IconView check = new XUi.IconView(this, XUi.IconView.CHECK, XUi.BLUE);
                check.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
                row.addView(check);
            }
            row.setOnClickListener(v -> {
                currentAccountId = a.id;
                prefs.edit().putLong("current_account", currentAccountId).apply();
                clearAccountCache();
                d.dismiss();
                renderHome();
            });
            row.setOnLongClickListener(v -> { d.dismiss(); showEditAccount(a.id); return true; });
            accountsBox.addView(row);
        }

        root.addView(drawerAction(XUi.IconView.PLUS, "Create account", () -> { d.dismiss(); showCreateAccount(); }));
        root.addView(drawerAction(XUi.IconView.PROFILE, "Generate random accounts", () -> { d.dismiss(); showRandomAccountGenerator(); }));
        root.addView(drawerAction(XUi.IconView.SETTINGS, "Bot settings", () -> { d.dismiss(); showBotSettings(); }));

        View divider2 = XUi.divider(this, pal.border);
        LinearLayout.LayoutParams divp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divp2.setMargins(0, dp(10), 0, dp(4));
        divider2.setLayoutParams(divp2);
        root.addView(divider2);

        root.addView(menuLine("Appearance", () -> { d.dismiss(); showAppearance(); }));
        root.addView(menuLine("Export universe", () -> { d.dismiss(); exportUniversePicker(); }));
        root.addView(menuLine("Import universe", () -> { d.dismiss(); importUniversePicker(); }));
        root.addView(menuLine("Reset demo universe", () -> { d.dismiss(); confirmReset(); }));

        ScrollView scroll = scrollOf(root);
        d.setContentView(scroll);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(pal.bg));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = (int)(getResources().getDisplayMetrics().widthPixels * 0.88f);
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.START;
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        root.setTranslationX(-getResources().getDisplayMetrics().widthPixels * 0.88f);
        root.animate().translationX(0).setDuration(190).start();
    }

    private View drawerAction(int icon, String text, Runnable action) {
        LinearLayout row = hbox();
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        XUi.IconView iv = new XUi.IconView(this, icon, pal.fg);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(28));
        ip.setMargins(0, 0, dp(18), 0);
        iv.setLayoutParams(ip);
        iv.setPadding(dp(2), dp(2), dp(2), dp(2));
        row.addView(iv);
        TextView label = tv(text, 19, pal.fg, true);
        row.addView(label);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private View drawerActionText(String text, Runnable action) {
        TextView row = tv(text, 16, pal.fg, true);
        row.setPadding(dp(4), dp(13), dp(4), dp(13));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private View menuLine(String text, Runnable action) {
        TextView row = tv(text, 16, pal.fg, true);
        row.setPadding(0, dp(15), 0, dp(15));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void showCreateAccount() {
        LinearLayout form = dialogForm();
        EditText name = field("Name", false);
        EditText handle = field("Handle", false);
        EditText bio = field("Bio", true);
        CheckBox verified = checkbox("Verified badge");
        CheckBox priv = checkbox("Private account");
        form.addView(name);
        form.addView(handle);
        form.addView(bio);
        form.addView(verified);
        form.addView(priv);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create local account")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
            String n = name.getText().toString().trim();
            String h = handle.getText().toString().trim();
            if (n.isEmpty() || h.isEmpty()) {
                Toast.makeText(this, "Name and handle are required", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int[] colors = {0xff7856a8, 0xffd05a7a, 0xff2f7d6f, 0xff536471, 0xffc28b32, 0xff1d9bf0, 0xffa45b35};
                int color = colors[(int) (System.currentTimeMillis() % colors.length)];
                long id = db.createAccount(n, h, bio.getText().toString(), color, verified.isChecked(), priv.isChecked());
                clearAccountCache();
                currentAccountId = id;
                prefs.edit().putLong("current_account", id).apply();
                dialog.dismiss();
                renderProfile(id);
            } catch (SQLiteConstraintException ex) {
                Toast.makeText(this, "That handle already exists locally", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void showRandomAccountGenerator() {
        LinearLayout form = dialogForm();
        EditText amount = field("How many accounts?", false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setText("10");
        CheckBox bots = checkbox("Make them Ollama AI bots");
        bots.setChecked(true);
        TextView note = tv("Handles are generated like real messy internet handles, not from a fixed name list. You can enter any positive amount; very large batches can take a while.", 13, pal.secondary, false);
        note.setPadding(0, dp(8), 0, dp(8));
        form.addView(amount);
        form.addView(bots);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Generate random accounts")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Generate", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
            int count;
            try { count = Integer.parseInt(amount.getText().toString().trim()); }
            catch (Exception e) { count = 0; }
            if (count <= 0) {
                Toast.makeText(this, "Enter a positive amount", Toast.LENGTH_SHORT).show();
                return;
            }
            final int requested = count;
            final boolean makeBots = bots.isChecked();
            dialog.dismiss();
            Toast.makeText(this, "Generating " + requested + " accounts…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int made = 0;
                int attempts = 0;
                while (made < requested && attempts < requested * 20L + 100) {
                    attempts++;
                    try {
                        String handle = randomHandle();
                        String name = randomDisplayName(handle);
                        String persona = randomPersona();
                        String bio = randomBio(persona);
                        int[] colors = {0xff7856a8,0xffd05a7a,0xff2f7d6f,0xff536471,0xffc28b32,0xff1d9bf0,0xffa45b35,0xff7a6b5d,0xff536d91};
                        int color = colors[random.nextInt(colors.length)];
                        long now = System.currentTimeMillis();
                        long oldest = new java.util.GregorianCalendar(2011, 0, 1).getTimeInMillis();
                        long joined = oldest + (long)(random.nextDouble() * Math.max(1L, now - oldest));
                        long id;
                        if (makeBots) id = db.createBotAccount(name, handle, bio, persona, color, joined);
                        else id = db.createAccount(name, handle, bio, color, false, false);
                        if (random.nextBoolean()) db.toggleFollow(id, currentAccountId);
                        if (random.nextInt(100) < 35) db.toggleFollow(currentAccountId, id);
                        made++;
                    } catch (Exception ignored) {}
                }
                final int total = made;
                runOnUiThread(() -> {
                    clearAccountCache();
                    Toast.makeText(this, "Created " + total + " account" + (total == 1 ? "" : "s"), Toast.LENGTH_LONG).show();
                    if (currentScreen == SCREEN_HOME) renderHome();
                });
            }).start();
        }));
        dialog.show();
    }

    private String randomHandle() {
        String[] a = {"mister","miss","tiny","sleepy","feral","weird","sad","loud","soft","cosmic","moldy","plastic","velvet","electric","local","cursed","noisy","empty","wet","crispy","stupid","evil","holy","baby","rotting","secret","fake","real","silly","lost","midnight","internet","microwave","sewer","parkinglot","basement"};
        String[] b = {"eggs","moth","frog","rat","pigeon","shrimp","teeth","soup","milk","toast","worm","worms","cloud","goblin","girl","boy","kisser","fan","enjoyer","department","machine","angel","devil","jpeg","pixel","socks","spoon","knife","banana","lemon","moss","bug","bat","cat","dog","fish","toaster","printer","lasagna","chair","orb","ghost","cowboy","wizard"};
        String left = a[random.nextInt(a.length)];
        String right = b[random.nextInt(b.length)];
        String[] patterns = {
                left + right,
                left + "_" + right,
                right + left,
                "the" + right,
                "not" + right,
                right + "kisser",
                right + "enjoyer",
                "mister" + right,
                left + right + (random.nextInt(90) + 10)
        };
        return patterns[random.nextInt(patterns.length)].toLowerCase(Locale.US);
    }

    private String randomDisplayName(String handle) {
        String[] extras = {"", "", "", "!!!", " online", " archive", " department", " posting", " enjoyer", " hater"};
        String base = handle.replace("_", " ");
        if (random.nextBoolean()) {
            String[] pieces = base.split(" ");
            StringBuilder b = new StringBuilder();
            for (String p : pieces) {
                if (p.isEmpty()) continue;
                if (b.length() > 0) b.append(" ");
                b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            base = b.toString();
        }
        return base + extras[random.nextInt(extras.length)];
    }

    private String randomPersona() {
        String[] moods = {"chronically online","dry and sarcastic","overly earnest","chaotic but friendly","low-energy and blunt","niche hobby obsessed","dramatic over tiny things","quiet lurker who occasionally posts","shitposter with occasional sincerity","rambling and specific","deadpan","enthusiastic and easily distracted"};
        String[] interests = {"music","movies","games","food","cats","fashion","coding","bad memes","art","football","anime","books","internet drama","photography","random facts","nothing in particular","old tech","horror","pop culture","cars","space","sleep"};
        return moods[random.nextInt(moods.length)] + ", mostly posts about " + interests[random.nextInt(interests.length)];
    }

    private String randomBio(String persona) {
        String[] bits = {"unfortunately online","i post and then regret it","no thoughts just posting","probably awake","professional nobody","do not perceive me","normal about things","posting through it","made of bad opinions","here for no reason","certified yapper","lurking mostly","internet resident"};
        if (random.nextInt(100) < 35) return bits[random.nextInt(bits.length)] + " · " + persona;
        return bits[random.nextInt(bits.length)];
    }

    private void showBotSettings() {
        LinearLayout form = dialogForm();
        CheckBox enabled = checkbox("Enable AI bot activity");
        enabled.setChecked(prefs.getBoolean("bots_enabled", false));
        EditText url = field("Ollama URL, e.g. http://192.168.1.50:11434", false);
        url.setText(prefs.getString("ollama_url", ""));
        EditText model = field("Ollama model", false);
        model.setText(prefs.getString("ollama_model", "gemma3:4b"));
        EditText min = field("Minimum seconds between actions per bot", false);
        min.setInputType(InputType.TYPE_CLASS_NUMBER);
        min.setText(String.valueOf(prefs.getInt("bot_min_seconds", 120)));
        EditText max = field("Maximum seconds between actions per bot", false);
        max.setInputType(InputType.TYPE_CLASS_NUMBER);
        max.setText(String.valueOf(prefs.getInt("bot_max_seconds", 1200)));

        TextView helper = tv("Bots act at independent random times. Text posts/replies/quotes/DMs come from Ollama; likes, reposts, follows, bookmarks and views are local. For a PC Ollama server, expose it to your LAN (for example OLLAMA_HOST=0.0.0.0:11434) and use the PC's LAN IP here.", 13, pal.secondary, false);
        helper.setPadding(0, dp(8), 0, dp(8));
        TextView status = tv("AI bot accounts: " + db.botCount(), 14, pal.fg, true);
        String last = prefs.getString("ollama_last_error", "");
        TextView lastError = tv(last.isEmpty() ? "No Ollama error recorded." : "Last Ollama error: " + last, 12, pal.secondary, false);
        lastError.setPadding(0, dp(5), 0, dp(8));
        TextView test = pill("Test Ollama connection", false);

        form.addView(enabled);
        form.addView(url);
        form.addView(model);
        form.addView(min);
        form.addView(max);
        form.addView(helper);
        form.addView(status);
        form.addView(lastError);
        form.addView(test);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Bot settings")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        Runnable persist = () -> {
            int minSec, maxSec;
            try { minSec = Math.max(15, Integer.parseInt(min.getText().toString().trim())); } catch (Exception e) { minSec = 120; }
            try { maxSec = Math.max(minSec, Integer.parseInt(max.getText().toString().trim())); } catch (Exception e) { maxSec = 1200; }
            prefs.edit()
                    .putBoolean("bots_enabled", enabled.isChecked())
                    .putString("ollama_url", url.getText().toString().trim())
                    .putString("ollama_model", model.getText().toString().trim())
                    .putInt("bot_min_seconds", minSec)
                    .putInt("bot_max_seconds", maxSec)
                    .apply();
        };

        dialog.setOnShowListener(v -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
                persist.run();
                dialog.dismiss();
                if (botEngine != null) botEngine.start();
            });
            test.setOnClickListener(x -> {
                persist.run();
                test.setEnabled(false);
                test.setText("Testing…");
                new Thread(() -> {
                    String result = BotEngine.testOllama(url.getText().toString(), model.getText().toString());
                    runOnUiThread(() -> {
                        test.setEnabled(true);
                        test.setText("Test Ollama connection");
                        new AlertDialog.Builder(this).setTitle("Ollama").setMessage(result).setPositiveButton("OK", null).show();
                    });
                }).start();
            });
        });
        dialog.show();
    }

    private void showEditAccount(long id) {
        Account a = db.getAccount(id);
        if (a == null) return;
        LinearLayout form = dialogForm();
        EditText name = field("Name", false); name.setText(a.name);
        EditText handle = field("Handle", false); handle.setText(a.handle);
        EditText bio = field("Bio", true); bio.setText(a.bio);
        EditText location = field("Location", false); location.setText(a.location == null ? "" : a.location);
        EditText website = field("Website", false); website.setText(a.website == null ? "" : a.website);
        EditText birth = field("Birthday / birth date", false); birth.setText(a.birthDate == null ? "" : a.birthDate);
        EditText joined = field("Joined date (YYYY-MM-DD)", false);
        joined.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(a.createdAt > 0 ? a.createdAt : System.currentTimeMillis())));

        EditText followers = field("Displayed followers (-1 = real)", false);
        followers.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        followers.setText(String.valueOf(a.displayFollowers));
        EditText following = field("Displayed following (-1 = real)", false);
        following.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        following.setText(String.valueOf(a.displayFollowing));

        CheckBox verified = checkbox("Verified badge"); verified.setChecked(a.verified);
        CheckBox priv = checkbox("Private account"); priv.setChecked(a.isPrivate);
        CheckBox bot = checkbox("Ollama AI bot account"); bot.setChecked(a.isBot);
        EditText persona = field("Bot persona / posting style", true);
        persona.setText(a.botPersona == null ? "" : a.botPersona);

        TextView avatar = pill("Avatar · choose / adjust", false);
        TextView banner = pill("Header · choose / adjust", false);
        LinearLayout images = hbox();
        LinearLayout.LayoutParams imp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        imp.setMargins(dp(3), dp(8), dp(3), dp(8));
        avatar.setLayoutParams(imp);
        LinearLayout.LayoutParams bmp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        bmp.setMargins(dp(3), dp(8), dp(3), dp(8));
        banner.setLayoutParams(bmp);
        images.addView(avatar);
        images.addView(banner);

        form.addView(name);
        form.addView(handle);
        form.addView(bio);
        form.addView(location);
        form.addView(website);
        form.addView(birth);
        form.addView(joined);
        form.addView(followers);
        form.addView(following);
        form.addView(verified);
        form.addView(priv);
        form.addView(bot);
        form.addView(persona);
        form.addView(images);

        ScrollView formScroll = scrollOf(form);
        formScroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Director · account")
                .setView(formScroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        Runnable save = () -> {
            a.name = name.getText().toString().trim();
            a.handle = handle.getText().toString().trim();
            a.bio = bio.getText().toString();
            a.location = location.getText().toString().trim();
            a.website = website.getText().toString().trim();
            a.birthDate = birth.getText().toString().trim();
            a.verified = verified.isChecked();
            a.isPrivate = priv.isChecked();
            a.isBot = bot.isChecked();
            a.botPersona = persona.getText().toString();
            try { a.displayFollowers = Long.parseLong(followers.getText().toString().trim()); } catch (Exception ex) { a.displayFollowers = -1; }
            try { a.displayFollowing = Long.parseLong(following.getText().toString().trim()); } catch (Exception ex) { a.displayFollowing = -1; }
            try {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(joined.getText().toString().trim());
                if (parsed != null) a.createdAt = parsed.getTime();
            } catch (Exception ignored) {}
            if (a.isBot && a.botNextAt <= 0) a.botNextAt = System.currentTimeMillis();
            try {
                db.updateAccount(a);
                clearAccountCache();
            } catch (SQLiteConstraintException ex) {
                Toast.makeText(this, "That handle already exists", Toast.LENGTH_SHORT).show();
            }
        };

        dialog.setOnShowListener(v -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(x -> {
                save.run();
                dialog.dismiss();
                refreshCurrent();
            });
            avatar.setOnClickListener(x -> {
                save.run();
                dialog.dismiss();
                chooseAccountImage(id, true, a.avatarPath);
            });
            banner.setOnClickListener(x -> {
                save.run();
                dialog.dismiss();
                chooseAccountImage(id, false, a.bannerPath);
            });
        });
        dialog.show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = vbox();
        form.setPadding(dp(18), dp(6), dp(18), dp(8));
        return form;
    }

    private EditText field(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(pal.secondary);
        e.setTextColor(pal.fg);
        e.setTextSize(15);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(XUi.BLUE));
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setMinLines(2);
            e.setMaxLines(5);
        }
        return e;
    }

    private CheckBox checkbox(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(pal.fg);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(XUi.BLUE));
        return c;
    }

    private void showDirectorMenu(long postId) {
        String[] options = {"Edit everything", "Make viral", "Duplicate / post as…", "Copy link", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle("Director Mode")
                .setItems(options, (d, which) -> {
                    if (which == 0) showDirectorEdit(postId);
                    else if (which == 1) {
                        Post p = db.getPost(postId);
                        if (p != null) {
                            p.views = 6_001L + random.nextInt(19_994_000);
                            p.likes = 6_001L + random.nextInt(494_000);
                            p.reposts = 6_001L + random.nextInt(194_000);
                            p.replies = 6_001L + random.nextInt(94_000);
                            p.bookmarks = 6_001L + random.nextInt(244_000);
                            p.viralBoost = 6_001L + random.nextInt(994_000);
                            db.updatePostDirector(p);
                            refreshCurrent();
                        }
                    } else if (which == 2) duplicatePost(postId);
                    else if (which == 3) showShareMenu(postId);
                    else if (which == 4) confirmDeletePost(postId);
                })
                .show();
    }

    private void showDirectorEdit(long postId) {
        Post p = db.getPost(postId);
        if (p == null) return;
        LinearLayout form = dialogForm();
        EditText body = field("Post text", true); body.setText(p.body);
        EditText views = numberField("Views", p.views);
        EditText likes = numberField("Likes", p.likes);
        EditText reposts = numberField("Reposts", p.reposts);
        EditText replies = numberField("Replies", p.replies);
        EditText bookmarks = numberField("Bookmarks", p.bookmarks);
        EditText mins = numberField("Minutes ago", Math.max(0, (System.currentTimeMillis() - p.createdAt) / 60000L));
        EditText boost = numberField("Recommendation boost", (long)p.viralBoost);
        TextView author = pill("Author: @" + account(p.authorId).handle, false);

        form.addView(body);
        form.addView(views);
        form.addView(likes);
        form.addView(reposts);
        form.addView(replies);
        form.addView(bookmarks);
        form.addView(mins);
        form.addView(boost);
        form.addView(author);

        final long[] chosenAuthor = {p.authorId};
        author.setOnClickListener(v -> chooseDirectorAuthor(chosenAuthor, author));

        new AlertDialog.Builder(this)
                .setTitle("Edit local post")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    p.body = body.getText().toString();
                    p.authorId = chosenAuthor[0];
                    p.views = parseLong(views, p.views);
                    p.likes = parseLong(likes, p.likes);
                    p.reposts = parseLong(reposts, p.reposts);
                    p.replies = parseLong(replies, p.replies);
                    p.bookmarks = parseLong(bookmarks, p.bookmarks);
                    long minutes = Math.max(0, parseLong(mins, 0));
                    p.createdAt = System.currentTimeMillis() - minutes * 60000L;
                    p.viralBoost = parseLong(boost, (long)p.viralBoost);
                    db.updatePostDirector(p);
                    refreshCurrent();
                })
                .show();
    }

    private EditText numberField(String hint, long value) {
        EditText e = field(hint, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setHint(hint);
        e.setText(String.valueOf(value));
        return e;
    }

    private long parseLong(EditText e, long fallback) {
        try { return Long.parseLong(e.getText().toString().trim()); }
        catch (Exception ex) { return fallback; }
    }

    private void chooseDirectorAuthor(final long[] chosen, TextView button) {
        List<Account> accounts = db.listAccounts();
        String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) names[i] = accounts.get(i).name + "  @" + accounts.get(i).handle;
        new AlertDialog.Builder(this)
                .setTitle("Choose author")
                .setItems(names, (d, which) -> {
                    chosen[0] = accounts.get(which).id;
                    button.setText("Author: @" + accounts.get(which).handle);
                })
                .show();
    }

    private void duplicatePost(long postId) {
        Post p = db.getPost(postId);
        if (p == null) return;
        composeDraft = p.body;
        composeMediaPath = p.mediaPath;
        composeAuthorId = currentAccountId;
        showComposer(null, p.quoteOf);
    }

    private void showPostMenu(long postId) {
        Post post = db.getPost(postId);
        boolean hasMedia = post != null && post.mediaPath != null && new File(post.mediaPath).exists();
        String[] options = hasMedia
                ? new String[]{"Copy link", "Quote", "Bookmark", "Save media", "Director Mode"}
                : new String[]{"Copy link", "Quote", "Bookmark", "Director Mode"};
        new AlertDialog.Builder(this)
                .setItems(options, (d, which) -> {
                    if (which == 0) showShareMenu(postId);
                    else if (which == 1) {
                        composeDraft = "";
                        composeMediaPath = null;
                        composeAuthorId = currentAccountId;
                        showComposer(null, postId);
                    } else if (which == 2) {
                        db.toggleInteraction(currentAccountId, postId, "bookmark");
                        refreshCurrent();
                    } else if (hasMedia && which == 3) {
                        savePostMedia(post.mediaPath);
                    } else {
                        showDirectorMenu(postId);
                    }
                })
                .show();
    }

    private void savePostMedia(String path) {
        if (path == null || !new File(path).exists()) {
            Toast.makeText(this, "That media file is missing", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingSaveMediaPath = path;
        String ext = ".jpg";
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String candidate = path.substring(dot).toLowerCase(Locale.US);
            if (candidate.matches("\\.(jpg|jpeg|png|webp|gif)")) ext = candidate;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_TITLE, "x-local-media-" + System.currentTimeMillis() + ext);
        startActivityForResult(i, SAVE_POST_MEDIA);
    }

    private void showShareMenu(long postId) {
        Post p = db.getPost(postId);
        if (p == null) return;
        Account a = account(p.authorId);
        if (a == null) return;
        String normal = "https://x.com/" + a.handle + "/status/" + p.id;
        String discord = "https://fxtwitter.com/" + a.handle + "/status/" + p.id;
        String[] options = {"Copy Link → Normal", "Copy Link → Discord (fxtwitter)"};
        new AlertDialog.Builder(this)
                .setTitle("Copy link")
                .setItems(options, (d, which) -> {
                    String value = which == 0 ? normal : discord;
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("post link", value));
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmDeletePost(long postId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this local post?")
                .setMessage("It only exists in this simulator.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    db.deletePost(postId);
                    if (currentScreen == SCREEN_POST && currentPostId == postId) renderHome();
                    else refreshCurrent();
                })
                .show();
    }

    private void showAppearance() {
        String[] items = {"Lights out", "Dim", "Light"};
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(items, themeMode, (d, which) -> {
                    themeMode = which;
                    prefs.edit().putInt("theme_mode", themeMode).apply();
                    pal = new XUi.Palette(themeMode);
                    applySystemBars();
                    d.dismiss();
                    refreshCurrent();
                })
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset the universe?")
                .setMessage("All local accounts, posts, follows, messages and interactions will be replaced by the demo universe.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    db.resetEverything();
                    clearAccountCache();
                    currentAccountId = -1;
                    ensureCurrentAccount();
                    renderHome();
                })
                .show();
    }

    private void chooseAccountImage(long accountId, boolean avatar, String currentPath) {
        pendingImageAccountId = accountId;
        int request = avatar ? PICK_AVATAR : PICK_BANNER;
        if (currentPath != null && new File(currentPath).exists()) {
            String[] options = {"Choose a new image", "Adjust current image"};
            new AlertDialog.Builder(this)
                    .setTitle(avatar ? "Profile picture" : "Header image")
                    .setItems(options, (d, which) -> {
                        if (which == 0) pickImage(request);
                        else showCropEditorFromPath(currentPath, accountId, avatar);
                    })
                    .setNegativeButton("Cancel", (d,w) -> renderProfile(accountId))
                    .show();
        } else {
            pickImage(request);
        }
    }

    private void showCropEditorFromUri(Uri uri, long accountId, boolean avatar) throws Exception {
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Cannot open image");
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new Exception("Couldn't decode image");
        showCropEditor(bitmap, accountId, avatar);
    }

    private void showCropEditorFromPath(String path, long accountId, boolean avatar) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            Toast.makeText(this, "Couldn't open the current image", Toast.LENGTH_SHORT).show();
            renderProfile(accountId);
            return;
        }
        showCropEditor(bitmap, accountId, avatar);
    }

    private void showCropEditor(Bitmap bitmap, long accountId, boolean avatar) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = vbox();

        LinearLayout top = hbox();
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        XUi.IconView close = new XUi.IconView(this, XUi.IconView.CLOSE, pal.fg);
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        top.addView(close);
        TextView title = tv(avatar ? "Adjust profile picture" : "Adjust header", 18, pal.fg, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        top.addView(title);
        TextView rotate = tv("Rotate", 14, XUi.BLUE, true);
        rotate.setGravity(Gravity.CENTER);
        rotate.setPadding(dp(10), 0, dp(10), 0);
        top.addView(rotate);
        TextView save = pill("Save", true);
        save.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        top.addView(save);
        root.addView(top);

        CropImageView crop = new CropImageView(this);
        crop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(crop);

        TextView hint = tv("Drag to move · pinch to resize · double-tap to reset", 13, pal.secondary, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(12), dp(10), dp(14));
        root.addView(hint);

        crop.post(() -> crop.setBitmap(bitmap, avatar ? 1f : 3f));
        close.setOnClickListener(v -> { d.dismiss(); renderProfile(accountId); });
        rotate.setOnClickListener(v -> crop.rotate90());
        save.setOnClickListener(v -> {
            try {
                Bitmap out = crop.renderCrop(avatar ? 1024 : 1500, avatar ? 1024 : 500);
                if (out == null) throw new Exception("Couldn't crop image");
                String path = saveBitmapToInternal(out);
                out.recycle();
                db.setAccountImage(accountId, avatar ? "avatar_path" : "banner_path", path);
                clearAccountCache();
                d.dismiss();
                renderProfile(accountId);
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't save image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        d.setContentView(root);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(pal.bg));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private String saveBitmapToInternal(Bitmap bitmap) throws Exception {
        File dir = new File(getFilesDir(), "media");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, UUID.randomUUID().toString() + ".jpg");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)) throw new Exception("Image compression failed");
        }
        return out.getAbsolutePath();
    }

    private void pickImage(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, request);
    }

    private void exportUniversePicker() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "x-local-universe.xuniverse");
        startActivityForResult(i, EXPORT_UNIVERSE);
    }

    private void importUniversePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, IMPORT_UNIVERSE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == PICK_POST_MEDIA) showComposer(composeReplyTo, composeQuoteOf);
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_AVATAR || requestCode == PICK_BANNER || requestCode == PICK_POST_MEDIA) {
                if (requestCode == PICK_POST_MEDIA) {
                    String path = copyImageToInternal(uri);
                    composeMediaPath = path;
                    showComposer(composeReplyTo, composeQuoteOf);
                } else if (pendingImageAccountId > 0) {
                    long id = pendingImageAccountId;
                    pendingImageAccountId = -1;
                    showCropEditorFromUri(uri, id, requestCode == PICK_AVATAR);
                }
            } else if (requestCode == SAVE_POST_MEDIA && pendingSaveMediaPath != null) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(pendingSaveMediaPath));
                     OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(uri))) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                pendingSaveMediaPath = null;
                Toast.makeText(this, "Media saved", Toast.LENGTH_SHORT).show();
            } else if (requestCode == EXPORT_UNIVERSE) {
                exportUniverse(uri);
            } else if (requestCode == IMPORT_UNIVERSE) {
                importUniverse(uri);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't complete that: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (requestCode == PICK_POST_MEDIA) showComposer(composeReplyTo, composeQuoteOf);
        }
    }

    private String copyImageToInternal(Uri uri) throws Exception {
        File dir = new File(getFilesDir(), "media");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, UUID.randomUUID().toString() + ".img");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            if (in == null) throw new Exception("Cannot open image");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out.getAbsolutePath();
    }

    private void exportUniverse(Uri uri) throws Exception {
        db.close();
        File dbFile = getDatabasePath(LocalDb.DB_NAME);
        try (OutputStream raw = getContentResolver().openOutputStream(uri);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            addFileToZip(zip, dbFile, "xlocal.db");
            File media = new File(getFilesDir(), "media");
            if (media.exists() && media.isDirectory()) {
                File[] files = media.listFiles();
                if (files != null) {
                    for (File f : files) if (f.isFile()) addFileToZip(zip, f, "media/" + f.getName());
                }
            }
        } finally {
            db = new LocalDb(this);
        }
        Toast.makeText(this, "Universe exported", Toast.LENGTH_SHORT).show();
    }

    private void addFileToZip(ZipOutputStream zip, File file, String name) throws Exception {
        if (!file.exists()) return;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
        }
        zip.closeEntry();
    }

    private void importUniverse(Uri uri) throws Exception {
        db.close();
        File database = getDatabasePath(LocalDb.DB_NAME);
        File media = new File(getFilesDir(), "media");
        File tempDb = new File(getCacheDir(), "imported-xlocal.db");
        if (tempDb.exists()) tempDb.delete();

        if (!media.exists()) media.mkdirs();
        File[] old = media.listFiles();
        if (old != null) for (File f : old) f.delete();

        boolean foundDb = false;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xlocal.db".equals(name)) {
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempDb))) {
                        int n;
                        while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    foundDb = true;
                } else if (name.startsWith("media/") && !name.contains("..")) {
                    String base = new File(name).getName();
                    File target = new File(media, base);
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        int n;
                        while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!foundDb) throw new Exception("Not a valid .xuniverse file");
        File parent = database.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        copyFile(tempDb, database);
        new File(database.getAbsolutePath() + "-wal").delete();
        new File(database.getAbsolutePath() + "-shm").delete();
        db = new LocalDb(this);
        clearAccountCache();
        currentAccountId = -1;
        ensureCurrentAccount();
        Toast.makeText(this, "Universe imported", Toast.LENGTH_SHORT).show();
        renderHome();
    }

    private void copyFile(File from, File to) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(from));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private Bitmap decodeScaled(String path, int maxW, int maxH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while (o.outWidth / sample > maxW * 2 || o.outHeight / sample > maxH * 2) sample *= 2;
        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = Math.max(1, sample);
        return BitmapFactory.decodeFile(path, real);
    }

    private void goBackFromSubscreen() {
        if (currentScreen == SCREEN_CHAT) renderMessages();
        else if (currentScreen == SCREEN_POST) renderHome();
        else if (currentScreen == SCREEN_FOLLOW_LIST && currentProfileId > 0) renderProfile(currentProfileId);
        else if (currentScreen == SCREEN_PROFILE || currentScreen == SCREEN_BOOKMARKS || currentScreen == SCREEN_DRAFTS) renderHome();
        else renderHome();
    }

    private void refreshCurrent() {
        clearAccountCache();
        if (currentScreen == SCREEN_HOME) renderHome();
        else if (currentScreen == SCREEN_SEARCH) renderSearch();
        else if (currentScreen == SCREEN_NOTIFICATIONS) renderNotifications();
        else if (currentScreen == SCREEN_MESSAGES) renderMessages();
        else if (currentScreen == SCREEN_PROFILE && currentProfileId > 0) renderProfile(currentProfileId);
        else if (currentScreen == SCREEN_POST && currentPostId > 0) renderPost(currentPostId);
        else if (currentScreen == SCREEN_BOOKMARKS) renderBookmarks();
        else if (currentScreen == SCREEN_CHAT && currentChatId > 0) renderChat(currentChatId);
        else if (currentScreen == SCREEN_DRAFTS) renderDrafts();
        else if (currentScreen == SCREEN_FOLLOW_LIST && currentProfileId > 0) renderFollowList(currentProfileId, currentFollowListFollowing);
        else renderHome();
    }

    private String formatCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) {
            double v = n / 1000.0;
            return trimOne(v) + "K";
        }
        if (n < 1_000_000_000) {
            double v = n / 1_000_000.0;
            return trimOne(v) + "M";
        }
        return trimOne(n / 1_000_000_000.0) + "B";
    }

    private String trimOne(double v) {
        if (v >= 100 || Math.abs(v - Math.rint(v)) < 0.05) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.1f", v);
    }

    private String timeAgo(long when) {
        long sec = Math.max(0, (System.currentTimeMillis() - when) / 1000);
        if (sec < 60) return sec + "s";
        long min = sec / 60;
        if (min < 60) return min + "m";
        long h = min / 60;
        if (h < 24) return h + "h";
        long d = h / 24;
        if (d < 7) return d + "d";
        return new SimpleDateFormat("MMM d", Locale.US).format(new Date(when));
    }
}
