package com.shinobriar.xlocal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LocalDb extends SQLiteOpenHelper {
    static final String DB_NAME = "xlocal.db";
    static final int DB_VERSION = 2;

    LocalDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "handle TEXT NOT NULL UNIQUE COLLATE NOCASE," +
                "bio TEXT NOT NULL DEFAULT ''," +
                "avatar_path TEXT," +
                "banner_path TEXT," +
                "color INTEGER NOT NULL," +
                "verified INTEGER NOT NULL DEFAULT 0," +
                "private INTEGER NOT NULL DEFAULT 0," +
                "display_followers INTEGER NOT NULL DEFAULT -1," +
                "display_following INTEGER NOT NULL DEFAULT -1," +
                "website TEXT NOT NULL DEFAULT ''," +
                "location TEXT NOT NULL DEFAULT ''," +
                "birth_date TEXT NOT NULL DEFAULT ''," +
                "is_bot INTEGER NOT NULL DEFAULT 0," +
                "bot_next_at INTEGER NOT NULL DEFAULT 0," +
                "bot_persona TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE follows (" +
                "follower_id INTEGER NOT NULL," +
                "following_id INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "PRIMARY KEY(follower_id, following_id))");

        db.execSQL("CREATE TABLE posts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "author_id INTEGER NOT NULL," +
                "body TEXT NOT NULL DEFAULT ''," +
                "media_path TEXT," +
                "reply_to INTEGER," +
                "quote_of INTEGER," +
                "created_at INTEGER NOT NULL," +
                "likes INTEGER NOT NULL DEFAULT 0," +
                "reposts INTEGER NOT NULL DEFAULT 0," +
                "replies INTEGER NOT NULL DEFAULT 0," +
                "views INTEGER NOT NULL DEFAULT 0," +
                "bookmarks INTEGER NOT NULL DEFAULT 0," +
                "viral_boost REAL NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE interactions (" +
                "account_id INTEGER NOT NULL," +
                "post_id INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "PRIMARY KEY(account_id, post_id, type))");

        db.execSQL("CREATE TABLE notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_id INTEGER NOT NULL," +
                "actor_id INTEGER NOT NULL," +
                "post_id INTEGER," +
                "type TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "read INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sender_id INTEGER NOT NULL," +
                "receiver_id INTEGER NOT NULL," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE drafts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "author_id INTEGER NOT NULL," +
                "body TEXT NOT NULL DEFAULT ''," +
                "media_path TEXT," +
                "reply_to INTEGER," +
                "quote_of INTEGER," +
                "created_at INTEGER NOT NULL)");

        seed(db);
    }

    private void seed(SQLiteDatabase db) {
        long you = insertAccount(db, "You", "you", "Your local account. Long-press posts to edit the universe.", 0xff536471, false, false);
        long luna = insertAccount(db, "Luna", "luna", "fictional person • coffee • photos", 0xff7856a8, false, false);
        long archive = insertAccount(db, "Pop Archive", "poparchive", "updates from this fictional little universe", 0xff1d9bf0, true, false);
        long mia = insertAccount(db, "Mia", "miawrites", "probably writing something instead of sleeping", 0xffd05a7a, false, false);

        insertFollowRaw(db, you, luna);
        insertFollowRaw(db, you, archive);
        insertFollowRaw(db, luna, you);
        insertFollowRaw(db, mia, luna);

        long now = System.currentTimeMillis();
        insertPostRaw(db, archive, "Welcome to your completely local timeline. Nothing here is connected to X.", null, null, null, now - 55 * 60_000L, 18400, 2300, 414, 821000, 2100, 9000);
        insertPostRaw(db, luna, "this app being able to fabricate an entire social circle is kind of dangerous actually", null, null, null, now - 22 * 60_000L, 1247, 91, 37, 28800, 94, 1200);
        insertPostRaw(db, mia, "if you can read this, the For You algorithm decided I was interesting enough", null, null, null, now - 8 * 60_000L, 88, 7, 4, 1430, 11, 250);
        insertPostRaw(db, you, "Long-press any post for Director Mode. Tap your avatar to create and switch accounts.", null, null, null, now - 2 * 60_000L, 12, 1, 2, 94, 3, 0);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN website TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE accounts ADD COLUMN location TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE accounts ADD COLUMN birth_date TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE accounts ADD COLUMN is_bot INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE accounts ADD COLUMN bot_next_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE accounts ADD COLUMN bot_persona TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "author_id INTEGER NOT NULL," +
                    "body TEXT NOT NULL DEFAULT ''," +
                    "media_path TEXT," +
                    "reply_to INTEGER," +
                    "quote_of INTEGER," +
                    "created_at INTEGER NOT NULL)");
        }
    }

    private long insertAccount(SQLiteDatabase db, String name, String handle, String bio, int color, boolean verified, boolean priv) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("handle", normalizeHandle(handle));
        v.put("bio", bio == null ? "" : bio);
        v.put("color", color);
        v.put("verified", verified ? 1 : 0);
        v.put("private", priv ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("accounts", null, v);
    }

    long createAccount(String name, String handle, String bio, int color, boolean verified, boolean priv) {
        return insertAccount(getWritableDatabase(), name, handle, bio, color, verified, priv);
    }

    void updateAccount(Account a) {
        ContentValues v = new ContentValues();
        v.put("name", a.name);
        v.put("handle", normalizeHandle(a.handle));
        v.put("bio", a.bio == null ? "" : a.bio);
        v.put("color", a.color);
        v.put("verified", a.verified ? 1 : 0);
        v.put("private", a.isPrivate ? 1 : 0);
        v.put("display_followers", a.displayFollowers);
        v.put("display_following", a.displayFollowing);
        v.put("website", a.website == null ? "" : a.website);
        v.put("location", a.location == null ? "" : a.location);
        v.put("birth_date", a.birthDate == null ? "" : a.birthDate);
        v.put("is_bot", a.isBot ? 1 : 0);
        v.put("bot_next_at", a.botNextAt);
        v.put("bot_persona", a.botPersona == null ? "" : a.botPersona);
        getWritableDatabase().update("accounts", v, "id=?", new String[]{String.valueOf(a.id)});
    }

    void setAccountImage(long accountId, String column, String path) {
        if (!"avatar_path".equals(column) && !"banner_path".equals(column)) return;
        ContentValues v = new ContentValues();
        v.put(column, path);
        getWritableDatabase().update("accounts", v, "id=?", new String[]{String.valueOf(accountId)});
    }

    Account getAccount(long id) {
        Cursor c = getReadableDatabase().query("accounts", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (c.moveToFirst()) return account(c);
            return null;
        } finally {
            c.close();
        }
    }

    List<Account> listAccounts() {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("accounts", null, null, null, null, null, "id ASC");
        try {
            while (c.moveToNext()) out.add(account(c));
        } finally {
            c.close();
        }
        return out;
    }

    boolean canSeeAccount(long viewerId, long targetId) {
        if (viewerId == targetId) return true;
        Account target = getAccount(targetId);
        if (target == null) return false;
        if (!target.isPrivate) return true;
        // In this simulator, a private account grants visibility by following the viewer back.
        return isFollowing(targetId, viewerId);
    }

    List<Account> listVisibleAccounts(long viewerId) {
        ArrayList<Account> out = new ArrayList<>();
        String sql = "SELECT a.* FROM accounts a WHERE a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?) " +
                "ORDER BY a.id ASC";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(viewerId), String.valueOf(viewerId)});
        try {
            while (c.moveToNext()) out.add(account(c));
        } finally {
            c.close();
        }
        return out;
    }

    List<Account> searchAccounts(String q, long viewerId) {
        ArrayList<Account> out = new ArrayList<>();
        String like = "%" + q + "%";
        String sql = "SELECT a.* FROM accounts a WHERE (a.name LIKE ? OR a.handle LIKE ? OR a.bio LIKE ?) " +
                "AND (a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY a.name COLLATE NOCASE ASC LIMIT 40";
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{like, like, like, String.valueOf(viewerId), String.valueOf(viewerId)});
        try {
            while (c.moveToNext()) out.add(account(c));
        } finally {
            c.close();
        }
        return out;
    }

    private Account account(Cursor c) {
        Account a = new Account();
        a.id = c.getLong(c.getColumnIndexOrThrow("id"));
        a.name = c.getString(c.getColumnIndexOrThrow("name"));
        a.handle = c.getString(c.getColumnIndexOrThrow("handle"));
        a.bio = c.getString(c.getColumnIndexOrThrow("bio"));
        a.avatarPath = c.getString(c.getColumnIndexOrThrow("avatar_path"));
        a.bannerPath = c.getString(c.getColumnIndexOrThrow("banner_path"));
        a.color = c.getInt(c.getColumnIndexOrThrow("color"));
        a.verified = c.getInt(c.getColumnIndexOrThrow("verified")) != 0;
        a.isPrivate = c.getInt(c.getColumnIndexOrThrow("private")) != 0;
        a.displayFollowers = c.getLong(c.getColumnIndexOrThrow("display_followers"));
        a.displayFollowing = c.getLong(c.getColumnIndexOrThrow("display_following"));
        a.website = c.getString(c.getColumnIndexOrThrow("website"));
        a.location = c.getString(c.getColumnIndexOrThrow("location"));
        a.birthDate = c.getString(c.getColumnIndexOrThrow("birth_date"));
        a.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        a.isBot = c.getInt(c.getColumnIndexOrThrow("is_bot")) != 0;
        a.botNextAt = c.getLong(c.getColumnIndexOrThrow("bot_next_at"));
        a.botPersona = c.getString(c.getColumnIndexOrThrow("bot_persona"));
        return a;
    }

    boolean toggleFollow(long followerId, long followingId) {
        if (followerId == followingId) return false;
        SQLiteDatabase db = getWritableDatabase();
        if (isFollowing(followerId, followingId)) {
            db.delete("follows", "follower_id=? AND following_id=?", new String[]{String.valueOf(followerId), String.valueOf(followingId)});
            return false;
        }
        insertFollowRaw(db, followerId, followingId);
        addNotification(db, followingId, followerId, null, "follow");
        return true;
    }

    boolean isFollowing(long followerId, long followingId) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM follows WHERE follower_id=? AND following_id=?",
                new String[]{String.valueOf(followerId), String.valueOf(followingId)}) > 0;
    }

    long actualFollowers(long accountId) {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM follows WHERE following_id=?", new String[]{String.valueOf(accountId)});
    }

    long actualFollowing(long accountId) {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM follows WHERE follower_id=?", new String[]{String.valueOf(accountId)});
    }

    private void insertFollowRaw(SQLiteDatabase db, long followerId, long followingId) {
        ContentValues v = new ContentValues();
        v.put("follower_id", followerId);
        v.put("following_id", followingId);
        v.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict("follows", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    long insertPost(long authorId, String body, String mediaPath, Long replyTo, Long quoteOf) {
        SQLiteDatabase db = getWritableDatabase();
        long id = insertPostRaw(db, authorId, body, mediaPath, replyTo, quoteOf, System.currentTimeMillis(), 0, 0, 0, 0, 0, 0);
        if (replyTo != null) {
            db.execSQL("UPDATE posts SET replies=replies+1 WHERE id=?", new Object[]{replyTo});
            Post parent = getPost(replyTo);
            if (parent != null && parent.authorId != authorId) addNotification(db, parent.authorId, authorId, replyTo, "reply");
        }
        return id;
    }

    private long insertPostRaw(SQLiteDatabase db, long authorId, String body, String mediaPath, Long replyTo, Long quoteOf, long createdAt,
                               long likes, long reposts, long replies, long views, long bookmarks, double viralBoost) {
        ContentValues v = new ContentValues();
        v.put("author_id", authorId);
        v.put("body", body == null ? "" : body);
        if (mediaPath != null) v.put("media_path", mediaPath);
        if (replyTo != null) v.put("reply_to", replyTo);
        if (quoteOf != null) v.put("quote_of", quoteOf);
        v.put("created_at", createdAt);
        v.put("likes", likes);
        v.put("reposts", reposts);
        v.put("replies", replies);
        v.put("views", views);
        v.put("bookmarks", bookmarks);
        v.put("viral_boost", viralBoost);
        return db.insertOrThrow("posts", null, v);
    }

    Post getPost(long id) {
        Cursor c = getReadableDatabase().query("posts", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? post(c) : null;
        } finally {
            c.close();
        }
    }

    List<Post> recentPosts(int limit) {
        return queryPosts(null, null, "created_at DESC", String.valueOf(limit));
    }

    List<Post> recentVisiblePosts(long viewerId, int limit) {
        String sql = "SELECT p.* FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?) " +
                "ORDER BY p.created_at DESC LIMIT ?";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(viewerId), String.valueOf(viewerId), String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    List<Post> followingPosts(long accountId, int limit) {
        String sql = "SELECT p.* FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE p.reply_to IS NULL AND (p.author_id=? OR p.author_id IN " +
                "(SELECT following_id FROM follows WHERE follower_id=?)) " +
                "AND (a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY p.created_at DESC LIMIT ?";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{
                String.valueOf(accountId), String.valueOf(accountId),
                String.valueOf(accountId), String.valueOf(accountId), String.valueOf(limit)});
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    List<Post> postsByAccount(long accountId, boolean includeReplies) {
        String where = includeReplies ? "author_id=?" : "author_id=? AND reply_to IS NULL";
        return queryPosts(where, new String[]{String.valueOf(accountId)}, "created_at DESC", "200");
    }

    List<Post> profileTimeline(long accountId, long viewerId) {
        String visible = "(a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?))";
        String sql =
                "SELECT p.*, p.created_at AS event_at, 0 AS is_profile_repost, ? AS profile_actor " +
                "FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE p.author_id=? AND p.reply_to IS NULL AND " + visible +
                " UNION ALL " +
                "SELECT p.*, i.created_at AS event_at, 1 AS is_profile_repost, ? AS profile_actor " +
                "FROM interactions i JOIN posts p ON p.id=i.post_id JOIN accounts a ON a.id=p.author_id " +
                "WHERE i.account_id=? AND i.type='repost' AND p.author_id<>? AND " + visible +
                " ORDER BY event_at DESC LIMIT 300";
        String aid = String.valueOf(accountId);
        String vid = String.valueOf(viewerId);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{
                aid, aid, vid, vid,
                aid, aid, aid, vid, vid
        });
        ArrayList<Post> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                Post p = post(c);
                p.profileEventAt = c.getLong(c.getColumnIndexOrThrow("event_at"));
                p.profileRepost = c.getInt(c.getColumnIndexOrThrow("is_profile_repost")) != 0;
                p.profileActorId = c.getLong(c.getColumnIndexOrThrow("profile_actor"));
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    List<Post> likedPosts(long accountId, long viewerId) {
        String sql = "SELECT p.* FROM posts p JOIN interactions i ON p.id=i.post_id " +
                "JOIN accounts a ON a.id=p.author_id " +
                "WHERE i.account_id=? AND i.type='like' AND " +
                "(a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY i.created_at DESC LIMIT 200";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(accountId), String.valueOf(viewerId), String.valueOf(viewerId)});
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    List<Post> searchPosts(String q, long viewerId) {
        String sql = "SELECT p.* FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE p.body LIKE ? AND (a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY p.created_at DESC LIMIT 100";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{"%" + q + "%", String.valueOf(viewerId), String.valueOf(viewerId)});
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    List<Post> repliesTo(long postId, long viewerId) {
        String sql = "SELECT p.* FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE p.reply_to=? AND (a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY p.created_at ASC LIMIT 200";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(postId), String.valueOf(viewerId), String.valueOf(viewerId)});
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    private List<Post> queryPosts(String selection, String[] args, String order, String limit) {
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("posts", null, selection, args, null, null, order, limit);
        try {
            while (c.moveToNext()) out.add(post(c));
        } finally {
            c.close();
        }
        return out;
    }

    private Post post(Cursor c) {
        Post p = new Post();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.authorId = c.getLong(c.getColumnIndexOrThrow("author_id"));
        p.body = c.getString(c.getColumnIndexOrThrow("body"));
        p.mediaPath = c.getString(c.getColumnIndexOrThrow("media_path"));
        int r = c.getColumnIndexOrThrow("reply_to");
        p.replyTo = c.isNull(r) ? null : c.getLong(r);
        int q = c.getColumnIndexOrThrow("quote_of");
        p.quoteOf = c.isNull(q) ? null : c.getLong(q);
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        p.likes = c.getLong(c.getColumnIndexOrThrow("likes"));
        p.reposts = c.getLong(c.getColumnIndexOrThrow("reposts"));
        p.replies = c.getLong(c.getColumnIndexOrThrow("replies"));
        p.views = c.getLong(c.getColumnIndexOrThrow("views"));
        p.bookmarks = c.getLong(c.getColumnIndexOrThrow("bookmarks"));
        p.viralBoost = c.getDouble(c.getColumnIndexOrThrow("viral_boost"));
        p.profileRepost = false;
        p.profileActorId = p.authorId;
        p.profileEventAt = p.createdAt;
        return p;
    }

    void updatePostDirector(Post p) {
        ContentValues v = new ContentValues();
        v.put("author_id", p.authorId);
        v.put("body", p.body == null ? "" : p.body);
        if (p.mediaPath == null) v.putNull("media_path"); else v.put("media_path", p.mediaPath);
        v.put("created_at", p.createdAt);
        v.put("likes", Math.max(0, p.likes));
        v.put("reposts", Math.max(0, p.reposts));
        v.put("replies", Math.max(0, p.replies));
        v.put("views", Math.max(0, p.views));
        v.put("bookmarks", Math.max(0, p.bookmarks));
        v.put("viral_boost", p.viralBoost);
        getWritableDatabase().update("posts", v, "id=?", new String[]{String.valueOf(p.id)});
    }

    void deletePost(long id) {
        SQLiteDatabase db = getWritableDatabase();
        Post p = getPost(id);
        if (p != null && p.replyTo != null) {
            db.execSQL("UPDATE posts SET replies=MAX(0,replies-1) WHERE id=?", new Object[]{p.replyTo});
        }
        db.delete("interactions", "post_id=?", new String[]{String.valueOf(id)});
        db.delete("notifications", "post_id=?", new String[]{String.valueOf(id)});
        db.delete("posts", "id=?", new String[]{String.valueOf(id)});
    }

    boolean hasInteraction(long accountId, long postId, String type) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM interactions WHERE account_id=? AND post_id=? AND type=?",
                new String[]{String.valueOf(accountId), String.valueOf(postId), type}) > 0;
    }

    boolean toggleInteraction(long accountId, long postId, String type) {
        SQLiteDatabase db = getWritableDatabase();
        boolean exists = hasInteraction(accountId, postId, type);
        String field = interactionField(type);
        if (exists) {
            db.delete("interactions", "account_id=? AND post_id=? AND type=?", new String[]{String.valueOf(accountId), String.valueOf(postId), type});
            if (field != null) db.execSQL("UPDATE posts SET " + field + "=MAX(0," + field + "-1) WHERE id=?", new Object[]{postId});
            return false;
        }

        ContentValues v = new ContentValues();
        v.put("account_id", accountId);
        v.put("post_id", postId);
        v.put("type", type);
        v.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict("interactions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (field != null) db.execSQL("UPDATE posts SET " + field + "=" + field + "+1 WHERE id=?", new Object[]{postId});

        Post target = getPost(postId);
        if (target != null && target.authorId != accountId && ("like".equals(type) || "repost".equals(type))) {
            addNotification(db, target.authorId, accountId, postId, type);
        }
        return true;
    }

    void addView(long accountId, long postId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("account_id", accountId);
        v.put("post_id", postId);
        v.put("type", "view");
        v.put("created_at", System.currentTimeMillis());
        long result = db.insertWithOnConflict("interactions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (result != -1) db.execSQL("UPDATE posts SET views=views+1 WHERE id=?", new Object[]{postId});
    }

    private String interactionField(String type) {
        if ("like".equals(type)) return "likes";
        if ("repost".equals(type)) return "reposts";
        if ("bookmark".equals(type)) return "bookmarks";
        return null;
    }

    private void addNotification(SQLiteDatabase db, long accountId, long actorId, Long postId, String type) {
        ContentValues v = new ContentValues();
        v.put("account_id", accountId);
        v.put("actor_id", actorId);
        if (postId != null) v.put("post_id", postId);
        v.put("type", type);
        v.put("created_at", System.currentTimeMillis());
        db.insert("notifications", null, v);
    }

    List<LocalNotification> notifications(long accountId) {
        ArrayList<LocalNotification> out = new ArrayList<>();
        String sql = "SELECT n.* FROM notifications n JOIN accounts a ON a.id=n.actor_id " +
                "WHERE n.account_id=? AND (a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                "ORDER BY n.created_at DESC LIMIT 150";
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{String.valueOf(accountId), String.valueOf(accountId), String.valueOf(accountId)});
        try {
            while (c.moveToNext()) {
                LocalNotification n = new LocalNotification();
                n.id = c.getLong(c.getColumnIndexOrThrow("id"));
                n.accountId = c.getLong(c.getColumnIndexOrThrow("account_id"));
                n.actorId = c.getLong(c.getColumnIndexOrThrow("actor_id"));
                int pi = c.getColumnIndexOrThrow("post_id");
                n.postId = c.isNull(pi) ? null : c.getLong(pi);
                n.type = c.getString(c.getColumnIndexOrThrow("type"));
                n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                n.read = c.getInt(c.getColumnIndexOrThrow("read")) != 0;
                out.add(n);
            }
        } finally {
            c.close();
        }
        return out;
    }

    void markNotificationsRead(long accountId) {
        ContentValues v = new ContentValues();
        v.put("read", 1);
        getWritableDatabase().update("notifications", v, "account_id=?", new String[]{String.valueOf(accountId)});
    }

    int unreadNotifications(long accountId) {
        return (int) DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM notifications n JOIN accounts a ON a.id=n.actor_id " +
                        "WHERE n.account_id=? AND n.read=0 AND (a.private=0 OR a.id=? OR EXISTS " +
                        "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?))",
                new String[]{String.valueOf(accountId), String.valueOf(accountId), String.valueOf(accountId)});
    }

    void sendMessage(long senderId, long receiverId, String body) {
        ContentValues v = new ContentValues();
        v.put("sender_id", senderId);
        v.put("receiver_id", receiverId);
        v.put("body", body);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("messages", null, v);
    }

    List<DirectMessage> conversation(long a, long b) {
        ArrayList<DirectMessage> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?) ORDER BY created_at ASC LIMIT 300",
                new String[]{String.valueOf(a), String.valueOf(b), String.valueOf(b), String.valueOf(a)});
        try {
            while (c.moveToNext()) {
                DirectMessage m = new DirectMessage();
                m.id = c.getLong(c.getColumnIndexOrThrow("id"));
                m.senderId = c.getLong(c.getColumnIndexOrThrow("sender_id"));
                m.receiverId = c.getLong(c.getColumnIndexOrThrow("receiver_id"));
                m.body = c.getString(c.getColumnIndexOrThrow("body"));
                m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    boolean hasMessages(long a, long b) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)",
                new String[]{String.valueOf(a), String.valueOf(b), String.valueOf(b), String.valueOf(a)}) > 0;
    }

    void resetEverything() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM messages");
            db.execSQL("DELETE FROM notifications");
            db.execSQL("DELETE FROM interactions");
            db.execSQL("DELETE FROM posts");
            db.execSQL("DELETE FROM follows");
            db.execSQL("DELETE FROM accounts");
            db.execSQL("DELETE FROM sqlite_sequence");
            seed(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String normalizeHandle(String value) {
        if (value == null) return "user";
        String s = value.trim();
        while (s.startsWith("@")) s = s.substring(1);
        s = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9_]", "");
        return s.isEmpty() ? "user" + System.currentTimeMillis() : s;
    }
}
