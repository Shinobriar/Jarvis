package com.shinobriar.xlocal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;

final class LocalDb extends SQLiteOpenHelper {
    static final String DB_NAME = "xlocal.db";
    static final int DB_VERSION = 4;

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
                "viral_boost REAL NOT NULL DEFAULT 0," +
                "location TEXT NOT NULL DEFAULT ''," +
                "poll_options TEXT NOT NULL DEFAULT ''," +
                "poll_counts TEXT NOT NULL DEFAULT '')");

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
                "group_id INTEGER," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE dm_groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL DEFAULT ''," +
                "avatar_path TEXT," +
                "creator_id INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE dm_group_members (" +
                "group_id INTEGER NOT NULL," +
                "account_id INTEGER NOT NULL," +
                "joined_at INTEGER NOT NULL," +
                "PRIMARY KEY(group_id, account_id))");

        db.execSQL("CREATE TABLE drafts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "author_id INTEGER NOT NULL," +
                "body TEXT NOT NULL DEFAULT ''," +
                "media_path TEXT," +
                "reply_to INTEGER," +
                "quote_of INTEGER," +
                "location TEXT NOT NULL DEFAULT ''," +
                "poll_options TEXT NOT NULL DEFAULT ''," +
                "scheduled_at INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE poll_votes (" +
                "account_id INTEGER NOT NULL," +
                "post_id INTEGER NOT NULL," +
                "option_index INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "PRIMARY KEY(account_id, post_id))");

        db.execSQL("CREATE TABLE scheduled_posts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "author_id INTEGER NOT NULL," +
                "body TEXT NOT NULL DEFAULT ''," +
                "media_path TEXT," +
                "reply_to INTEGER," +
                "quote_of INTEGER," +
                "location TEXT NOT NULL DEFAULT ''," +
                "poll_options TEXT NOT NULL DEFAULT ''," +
                "publish_at INTEGER NOT NULL)");

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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE posts ADD COLUMN location TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE posts ADD COLUMN poll_options TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE posts ADD COLUMN poll_counts TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS poll_votes (" +
                    "account_id INTEGER NOT NULL," +
                    "post_id INTEGER NOT NULL," +
                    "option_index INTEGER NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "PRIMARY KEY(account_id, post_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS scheduled_posts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "author_id INTEGER NOT NULL," +
                    "body TEXT NOT NULL DEFAULT ''," +
                    "media_path TEXT," +
                    "reply_to INTEGER," +
                    "quote_of INTEGER," +
                    "location TEXT NOT NULL DEFAULT ''," +
                    "poll_options TEXT NOT NULL DEFAULT ''," +
                    "publish_at INTEGER NOT NULL)");
            db.execSQL("ALTER TABLE drafts ADD COLUMN location TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE drafts ADD COLUMN poll_options TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE drafts ADD COLUMN scheduled_at INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE messages ADD COLUMN group_id INTEGER");
            db.execSQL("CREATE TABLE IF NOT EXISTS dm_groups (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL DEFAULT ''," +
                    "avatar_path TEXT," +
                    "creator_id INTEGER NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS dm_group_members (" +
                    "group_id INTEGER NOT NULL," +
                    "account_id INTEGER NOT NULL," +
                    "joined_at INTEGER NOT NULL," +
                    "PRIMARY KEY(group_id, account_id))");
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
        if (a.createdAt > 0) v.put("created_at", a.createdAt);
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

    Account getAccountByHandle(String handle) {
        if (handle == null) return null;
        String h = normalizeHandle(handle);
        Cursor c = getReadableDatabase().query("accounts", null, "handle=? COLLATE NOCASE",
                new String[]{h}, null, null, null, "1");
        try { return c.moveToFirst() ? account(c) : null; }
        finally { c.close(); }
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

    List<Account> followingAccounts(long accountId) {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT a.* FROM accounts a JOIN follows f ON a.id=f.following_id WHERE f.follower_id=? ORDER BY f.created_at DESC",
                new String[]{String.valueOf(accountId)});
        try { while (c.moveToNext()) out.add(account(c)); } finally { c.close(); }
        return out;
    }

    List<Account> followerAccounts(long accountId) {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT a.* FROM accounts a JOIN follows f ON a.id=f.follower_id WHERE f.following_id=? ORDER BY f.created_at DESC",
                new String[]{String.valueOf(accountId)});
        try { while (c.moveToNext()) out.add(account(c)); } finally { c.close(); }
        return out;
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

    long insertPostWithExtras(long authorId, String body, String mediaPath, Long replyTo, Long quoteOf,
                              String location, List<String> pollOptions) {
        long id = insertPost(authorId, body, mediaPath, replyTo, quoteOf);
        setPostExtras(id, location, pollOptions);
        return id;
    }

    private void setPostExtras(long postId, String location, List<String> pollOptions) {
        ContentValues v = new ContentValues();
        v.put("location", location == null ? "" : location.trim());
        String encoded = encodePollOptions(pollOptions);
        v.put("poll_options", encoded);
        if (encoded.isEmpty()) {
            v.put("poll_counts", "");
        } else {
            String[] parts = encoded.split("\\u001F", -1);
            StringBuilder counts = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) counts.append(",");
                counts.append("0");
            }
            v.put("poll_counts", counts.toString());
        }
        getWritableDatabase().update("posts", v, "id=?", new String[]{String.valueOf(postId)});
    }

    long schedulePost(long authorId, String body, String mediaPath, Long replyTo, Long quoteOf,
                      String location, List<String> pollOptions, long publishAt) {
        ContentValues v = new ContentValues();
        v.put("author_id", authorId);
        v.put("body", body == null ? "" : body);
        if (mediaPath == null) v.putNull("media_path"); else v.put("media_path", mediaPath);
        if (replyTo == null) v.putNull("reply_to"); else v.put("reply_to", replyTo);
        if (quoteOf == null) v.putNull("quote_of"); else v.put("quote_of", quoteOf);
        v.put("location", location == null ? "" : location.trim());
        v.put("poll_options", encodePollOptions(pollOptions));
        v.put("publish_at", publishAt);
        return getWritableDatabase().insertOrThrow("scheduled_posts", null, v);
    }

    int publishDueScheduled() {
        SQLiteDatabase db = getWritableDatabase();
        ArrayList<Long> done = new ArrayList<>();
        int published = 0;
        Cursor c = db.query("scheduled_posts", null, "publish_at<=?",
                new String[]{String.valueOf(System.currentTimeMillis())}, null, null, "publish_at ASC");
        try {
            while (c.moveToNext()) {
                long sid = c.getLong(c.getColumnIndexOrThrow("id"));
                long authorId = c.getLong(c.getColumnIndexOrThrow("author_id"));
                String body = c.getString(c.getColumnIndexOrThrow("body"));
                String media = c.getString(c.getColumnIndexOrThrow("media_path"));
                int rr = c.getColumnIndexOrThrow("reply_to");
                Long replyTo = c.isNull(rr) ? null : c.getLong(rr);
                int qq = c.getColumnIndexOrThrow("quote_of");
                Long quoteOf = c.isNull(qq) ? null : c.getLong(qq);
                String location = c.getString(c.getColumnIndexOrThrow("location"));
                String encodedPoll = c.getString(c.getColumnIndexOrThrow("poll_options"));
                long publishAt = c.getLong(c.getColumnIndexOrThrow("publish_at"));

                List<String> options = decodePollOptions(encodedPoll);
                long postId = insertPostWithExtras(authorId, body, media, replyTo, quoteOf, location, options);
                ContentValues time = new ContentValues();
                time.put("created_at", publishAt);
                db.update("posts", time, "id=?", new String[]{String.valueOf(postId)});
                done.add(sid);
                published++;
            }
        } finally { c.close(); }
        for (Long id : done) db.delete("scheduled_posts", "id=?", new String[]{String.valueOf(id)});
        return published;
    }

    boolean votePoll(long accountId, long postId, int optionIndex) {
        Post p = getPost(postId);
        if (p == null || p.pollOptions == null || optionIndex < 0 || optionIndex >= p.pollOptions.length) return false;
        SQLiteDatabase db = getWritableDatabase();
        if (DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM poll_votes WHERE account_id=? AND post_id=?",
                new String[]{String.valueOf(accountId), String.valueOf(postId)}) > 0) return false;

        ContentValues vote = new ContentValues();
        vote.put("account_id", accountId);
        vote.put("post_id", postId);
        vote.put("option_index", optionIndex);
        vote.put("created_at", System.currentTimeMillis());
        db.insertOrThrow("poll_votes", null, vote);

        int[] counts = p.pollCounts == null ? new int[p.pollOptions.length] : p.pollCounts.clone();
        if (counts.length != p.pollOptions.length) counts = new int[p.pollOptions.length];
        counts[optionIndex]++;
        ContentValues v = new ContentValues();
        v.put("poll_counts", encodePollCounts(counts));
        db.update("posts", v, "id=?", new String[]{String.valueOf(postId)});
        return true;
    }

    int pollVoteFor(long accountId, long postId) {
        Cursor c = getReadableDatabase().query("poll_votes", new String[]{"option_index"},
                "account_id=? AND post_id=?", new String[]{String.valueOf(accountId), String.valueOf(postId)},
                null, null, null, "1");
        try { return c.moveToFirst() ? c.getInt(0) : -1; }
        finally { c.close(); }
    }

    private String encodePollOptions(List<String> options) {
        if (options == null || options.isEmpty()) return "";
        ArrayList<String> clean = new ArrayList<>();
        for (String option : options) {
            if (option == null) continue;
            String s = option.trim().replace("\u001F", " ");
            if (!s.isEmpty()) clean.add(s);
            if (clean.size() >= 4) break;
        }
        if (clean.size() < 2) return "";
        StringBuilder b = new StringBuilder();
        for (String s : clean) {
            if (b.length() > 0) b.append('\u001F');
            b.append(s);
        }
        return b.toString();
    }

    private List<String> decodePollOptions(String encoded) {
        ArrayList<String> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return out;
        String[] parts = encoded.split("\\u001F", -1);
        for (String part : parts) if (!part.isEmpty()) out.add(part);
        return out;
    }

    private String encodePollCounts(int[] counts) {
        if (counts == null || counts.length == 0) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (i > 0) b.append(",");
            b.append(Math.max(0, counts[i]));
        }
        return b.toString();
    }

    private int[] decodePollCounts(String encoded, int size) {
        int[] out = new int[Math.max(0, size)];
        if (encoded == null || encoded.isEmpty()) return out;
        String[] parts = encoded.split(",");
        for (int i = 0; i < out.length && i < parts.length; i++) {
            try { out[i] = Math.max(0, Integer.parseInt(parts[i])); } catch (Exception ignored) {}
        }
        return out;
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

    List<Post> timelinePosts(long viewerId, boolean followingOnly, int limit) {
        String visibleAuthor = "(a.private=0 OR a.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?))";
        String visibleReposter = "(ra.private=0 OR ra.id=? OR EXISTS " +
                "(SELECT 1 FROM follows vr WHERE vr.follower_id=ra.id AND vr.following_id=?))";
        String actorFilterPost = followingOnly
                ? " AND (p.author_id=? OR p.author_id IN (SELECT following_id FROM follows WHERE follower_id=?))"
                : "";
        String actorFilterRepost = followingOnly
                ? " AND (i.account_id=? OR i.account_id IN (SELECT following_id FROM follows WHERE follower_id=?))"
                : "";

        String sql =
                "SELECT p.*, p.created_at AS event_at, 0 AS is_profile_repost, p.author_id AS profile_actor " +
                "FROM posts p JOIN accounts a ON a.id=p.author_id " +
                "WHERE p.reply_to IS NULL AND " + visibleAuthor + actorFilterPost +
                " UNION ALL " +
                "SELECT p.*, i.created_at AS event_at, 1 AS is_profile_repost, i.account_id AS profile_actor " +
                "FROM interactions i JOIN posts p ON p.id=i.post_id " +
                "JOIN accounts a ON a.id=p.author_id JOIN accounts ra ON ra.id=i.account_id " +
                "WHERE i.type='repost' AND p.reply_to IS NULL AND " + visibleAuthor +
                " AND " + visibleReposter + actorFilterRepost +
                " ORDER BY event_at DESC LIMIT ?";

        ArrayList<String> args = new ArrayList<>();
        String vid = String.valueOf(viewerId);
        args.add(vid); args.add(vid);
        if (followingOnly) { args.add(vid); args.add(vid); }
        args.add(vid); args.add(vid);
        args.add(vid); args.add(vid);
        if (followingOnly) { args.add(vid); args.add(vid); }
        args.add(String.valueOf(limit));

        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                Post p = post(c);
                p.profileEventAt = c.getLong(c.getColumnIndexOrThrow("event_at"));
                p.profileRepost = c.getInt(c.getColumnIndexOrThrow("is_profile_repost")) != 0;
                p.profileActorId = c.getLong(c.getColumnIndexOrThrow("profile_actor"));
                out.add(p);
            }
        } finally { c.close(); }
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
                "WHERE i.account_id=? AND i.type='repost' AND " + visible +
                " ORDER BY event_at DESC LIMIT 300";
        String aid = String.valueOf(accountId);
        String vid = String.valueOf(viewerId);
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{
                aid, aid, vid, vid,
                aid, aid, vid, vid
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

    int threadReplyCount(long rootPostId, long viewerId) {
        String sql =
                "WITH RECURSIVE thread(id,depth) AS (" +
                " SELECT p.id,1 FROM posts p JOIN accounts a ON a.id=p.author_id " +
                " WHERE p.reply_to=? AND (a.private=0 OR a.id=? OR EXISTS " +
                " (SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                " UNION ALL " +
                " SELECT child.id,thread.depth+1 FROM posts child JOIN thread ON child.reply_to=thread.id " +
                " JOIN accounts a ON a.id=child.author_id " +
                " WHERE a.private=0 OR a.id=? OR EXISTS " +
                " (SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)" +
                ") SELECT COUNT(*) FROM thread";
        return (int) DatabaseUtils.longForQuery(getReadableDatabase(), sql, new String[]{
                String.valueOf(rootPostId), String.valueOf(viewerId), String.valueOf(viewerId),
                String.valueOf(viewerId), String.valueOf(viewerId)
        });
    }

    List<Post> threadReplies(long rootPostId, long viewerId, int limit) {
        String sql =
                "WITH RECURSIVE thread(id,depth) AS (" +
                " SELECT p.id,1 FROM posts p JOIN accounts a ON a.id=p.author_id " +
                " WHERE p.reply_to=? AND (a.private=0 OR a.id=? OR EXISTS " +
                " (SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)) " +
                " UNION ALL " +
                " SELECT child.id,thread.depth+1 FROM posts child JOIN thread ON child.reply_to=thread.id " +
                " JOIN accounts a ON a.id=child.author_id " +
                " WHERE a.private=0 OR a.id=? OR EXISTS " +
                " (SELECT 1 FROM follows vf WHERE vf.follower_id=a.id AND vf.following_id=?)" +
                ") SELECT p.*,thread.depth AS thread_depth FROM thread JOIN posts p ON p.id=thread.id " +
                " ORDER BY p.created_at ASC LIMIT ?";
        ArrayList<Post> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{
                String.valueOf(rootPostId), String.valueOf(viewerId), String.valueOf(viewerId),
                String.valueOf(viewerId), String.valueOf(viewerId), String.valueOf(Math.max(1, limit))
        });
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
        p.location = c.getString(c.getColumnIndexOrThrow("location"));
        List<String> poll = decodePollOptions(c.getString(c.getColumnIndexOrThrow("poll_options")));
        p.pollOptions = poll.toArray(new String[0]);
        p.pollCounts = decodePollCounts(c.getString(c.getColumnIndexOrThrow("poll_counts")), p.pollOptions.length);
        int depthColumn = c.getColumnIndex("thread_depth");
        p.threadDepth = depthColumn >= 0 ? c.getInt(depthColumn) : 0;
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
        db.delete("poll_votes", "post_id=?", new String[]{String.valueOf(id)});
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

    boolean ensureInteraction(long accountId, long postId, String type) {
        if (hasInteraction(accountId, postId, type)) return false;
        return toggleInteraction(accountId, postId, type);
    }

    void bumpEngagement(long postId, long addViews, long addLikes, long addReposts, long addReplies, long addBookmarks) {
        getWritableDatabase().execSQL(
                "UPDATE posts SET views=MAX(0,views+?), likes=MAX(0,likes+?), reposts=MAX(0,reposts+?), " +
                        "replies=MAX(0,replies+?), bookmarks=MAX(0,bookmarks+?) WHERE id=?",
                new Object[]{Math.max(0,addViews), Math.max(0,addLikes), Math.max(0,addReposts),
                        Math.max(0,addReplies), Math.max(0,addBookmarks), postId});
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
        v.putNull("group_id");
        v.put("body", body);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("messages", null, v);
    }

    List<DirectMessage> conversation(long a, long b) {
        ArrayList<DirectMessage> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM messages WHERE group_id IS NULL AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)) ORDER BY created_at ASC LIMIT 300",
                new String[]{String.valueOf(a), String.valueOf(b), String.valueOf(b), String.valueOf(a)});
        try {
            while (c.moveToNext()) {
                DirectMessage m = new DirectMessage();
                m.id = c.getLong(c.getColumnIndexOrThrow("id"));
                m.senderId = c.getLong(c.getColumnIndexOrThrow("sender_id"));
                m.receiverId = c.getLong(c.getColumnIndexOrThrow("receiver_id"));
                int groupCol = c.getColumnIndex("group_id");
                m.groupId = groupCol >= 0 && !c.isNull(groupCol) ? c.getLong(groupCol) : null;
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
                "SELECT COUNT(*) FROM messages WHERE group_id IS NULL AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                new String[]{String.valueOf(a), String.valueOf(b), String.valueOf(b), String.valueOf(a)}) > 0;
    }

    void updateMessage(long messageId, long editorAccountId, String body) {
        ContentValues v = new ContentValues();
        v.put("body", body == null ? "" : body);
        getWritableDatabase().update("messages", v, "id=? AND sender_id=?",
                new String[]{String.valueOf(messageId), String.valueOf(editorAccountId)});
    }

    void deleteMessage(long messageId, long editorAccountId) {
        getWritableDatabase().delete("messages", "id=? AND sender_id=?",
                new String[]{String.valueOf(messageId), String.valueOf(editorAccountId)});
    }

    long createGroup(String name, String avatarPath, long creatorId, List<Long> memberIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues g = new ContentValues();
            String cleanName = name == null ? "" : name.trim();
            g.put("name", cleanName.isEmpty() ? "Group" : cleanName);
            if (avatarPath == null || avatarPath.isEmpty()) g.putNull("avatar_path"); else g.put("avatar_path", avatarPath);
            g.put("creator_id", creatorId);
            g.put("created_at", System.currentTimeMillis());
            long groupId = db.insertOrThrow("dm_groups", null, g);

            LinkedHashSet<Long> unique = new LinkedHashSet<>();
            unique.add(creatorId);
            if (memberIds != null) unique.addAll(memberIds);
            long now = System.currentTimeMillis();
            for (Long accountId : unique) {
                if (accountId == null || getAccount(accountId) == null) continue;
                ContentValues m = new ContentValues();
                m.put("group_id", groupId);
                m.put("account_id", accountId);
                m.put("joined_at", now);
                db.insertWithOnConflict("dm_group_members", null, m, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
            return groupId;
        } finally {
            db.endTransaction();
        }
    }

    GroupChat getGroup(long groupId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT g.*, (SELECT COUNT(*) FROM dm_group_members gm WHERE gm.group_id=g.id) AS member_count " +
                        "FROM dm_groups g WHERE g.id=?",
                new String[]{String.valueOf(groupId)});
        try {
            return c.moveToFirst() ? group(c) : null;
        } finally { c.close(); }
    }

    List<GroupChat> groupsFor(long accountId) {
        ArrayList<GroupChat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT g.*, (SELECT COUNT(*) FROM dm_group_members gm2 WHERE gm2.group_id=g.id) AS member_count, " +
                        "(SELECT MAX(m.created_at) FROM messages m WHERE m.group_id=g.id) AS last_at " +
                        "FROM dm_groups g JOIN dm_group_members gm ON gm.group_id=g.id " +
                        "WHERE gm.account_id=? ORDER BY COALESCE(last_at,g.created_at) DESC, g.id DESC",
                new String[]{String.valueOf(accountId)});
        try {
            while (c.moveToNext()) out.add(group(c));
        } finally { c.close(); }
        return out;
    }

    private GroupChat group(Cursor c) {
        GroupChat g = new GroupChat();
        g.id = c.getLong(c.getColumnIndexOrThrow("id"));
        g.name = c.getString(c.getColumnIndexOrThrow("name"));
        g.avatarPath = c.getString(c.getColumnIndexOrThrow("avatar_path"));
        g.creatorId = c.getLong(c.getColumnIndexOrThrow("creator_id"));
        g.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int mc = c.getColumnIndex("member_count");
        g.memberCount = mc >= 0 ? c.getInt(mc) : 0;
        return g;
    }

    boolean isGroupMember(long groupId, long accountId) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM dm_group_members WHERE group_id=? AND account_id=?",
                new String[]{String.valueOf(groupId), String.valueOf(accountId)}) > 0;
    }

    List<Account> groupMembers(long groupId) {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT a.* FROM accounts a JOIN dm_group_members gm ON gm.account_id=a.id " +
                        "WHERE gm.group_id=? ORDER BY gm.joined_at ASC, a.id ASC",
                new String[]{String.valueOf(groupId)});
        try { while (c.moveToNext()) out.add(account(c)); }
        finally { c.close(); }
        return out;
    }

    void addGroupMembers(long groupId, List<Long> memberIds) {
        if (memberIds == null) return;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        for (Long accountId : new LinkedHashSet<>(memberIds)) {
            if (accountId == null || getAccount(accountId) == null) continue;
            ContentValues m = new ContentValues();
            m.put("group_id", groupId);
            m.put("account_id", accountId);
            m.put("joined_at", now);
            db.insertWithOnConflict("dm_group_members", null, m, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    void updateGroup(long groupId, String name, String avatarPath) {
        ContentValues v = new ContentValues();
        v.put("name", name == null || name.trim().isEmpty() ? "Group" : name.trim());
        if (avatarPath == null || avatarPath.isEmpty()) v.putNull("avatar_path"); else v.put("avatar_path", avatarPath);
        getWritableDatabase().update("dm_groups", v, "id=?", new String[]{String.valueOf(groupId)});
    }

    void setGroupAvatar(long groupId, String avatarPath) {
        ContentValues v = new ContentValues();
        if (avatarPath == null || avatarPath.isEmpty()) v.putNull("avatar_path"); else v.put("avatar_path", avatarPath);
        getWritableDatabase().update("dm_groups", v, "id=?", new String[]{String.valueOf(groupId)});
    }

    void leaveGroup(long groupId, long accountId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("dm_group_members", "group_id=? AND account_id=?",
                new String[]{String.valueOf(groupId), String.valueOf(accountId)});
        long remaining = DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM dm_group_members WHERE group_id=?",
                new String[]{String.valueOf(groupId)});
        if (remaining <= 0) {
            db.delete("messages", "group_id=?", new String[]{String.valueOf(groupId)});
            db.delete("dm_groups", "id=?", new String[]{String.valueOf(groupId)});
        }
    }

    void sendGroupMessage(long senderId, long groupId, String body) {
        if (!isGroupMember(groupId, senderId)) return;
        ContentValues v = new ContentValues();
        v.put("sender_id", senderId);
        v.put("receiver_id", 0);
        v.put("group_id", groupId);
        v.put("body", body == null ? "" : body);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("messages", null, v);
    }

    List<DirectMessage> groupConversation(long groupId) {
        ArrayList<DirectMessage> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "group_id=?",
                new String[]{String.valueOf(groupId)}, null, null, "created_at ASC", "500");
        try {
            while (c.moveToNext()) {
                DirectMessage m = new DirectMessage();
                m.id = c.getLong(c.getColumnIndexOrThrow("id"));
                m.senderId = c.getLong(c.getColumnIndexOrThrow("sender_id"));
                m.receiverId = c.getLong(c.getColumnIndexOrThrow("receiver_id"));
                m.groupId = groupId;
                m.body = c.getString(c.getColumnIndexOrThrow("body"));
                m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                out.add(m);
            }
        } finally { c.close(); }
        return out;
    }

    String groupLastMessage(long groupId) {
        Cursor c = getReadableDatabase().query("messages", new String[]{"body"}, "group_id=?",
                new String[]{String.valueOf(groupId)}, null, null, "created_at DESC", "1");
        try { return c.moveToFirst() ? c.getString(0) : ""; }
        finally { c.close(); }
    }

    long saveDraft(long existingId, long authorId, String body, String mediaPath, Long replyTo, Long quoteOf) {
        return saveDraft(existingId, authorId, body, mediaPath, replyTo, quoteOf, "", null, 0);
    }

    long saveDraft(long existingId, long authorId, String body, String mediaPath, Long replyTo, Long quoteOf,
                   String location, List<String> pollOptions, long scheduledAt) {
        ContentValues v = new ContentValues();
        v.put("author_id", authorId);
        v.put("body", body == null ? "" : body);
        if (mediaPath == null) v.putNull("media_path"); else v.put("media_path", mediaPath);
        if (replyTo == null) v.putNull("reply_to"); else v.put("reply_to", replyTo);
        if (quoteOf == null) v.putNull("quote_of"); else v.put("quote_of", quoteOf);
        v.put("location", location == null ? "" : location.trim());
        v.put("poll_options", encodePollOptions(pollOptions));
        v.put("scheduled_at", Math.max(0, scheduledAt));
        v.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        if (existingId > 0) {
            db.update("drafts", v, "id=?", new String[]{String.valueOf(existingId)});
            return existingId;
        }
        return db.insert("drafts", null, v);
    }

    DraftPost getDraft(long id) {
        Cursor c = getReadableDatabase().query("drafts", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return draft(c);
        } finally { c.close(); }
    }

    List<DraftPost> drafts(long authorId) {
        ArrayList<DraftPost> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("drafts", null, "author_id=?", new String[]{String.valueOf(authorId)},
                null, null, "created_at DESC", "200");
        try { while (c.moveToNext()) out.add(draft(c)); } finally { c.close(); }
        return out;
    }

    void deleteDraft(long id) {
        getWritableDatabase().delete("drafts", "id=?", new String[]{String.valueOf(id)});
    }

    private DraftPost draft(Cursor c) {
        DraftPost d = new DraftPost();
        d.id = c.getLong(c.getColumnIndexOrThrow("id"));
        d.authorId = c.getLong(c.getColumnIndexOrThrow("author_id"));
        d.body = c.getString(c.getColumnIndexOrThrow("body"));
        d.mediaPath = c.getString(c.getColumnIndexOrThrow("media_path"));
        int r = c.getColumnIndexOrThrow("reply_to");
        d.replyTo = c.isNull(r) ? null : c.getLong(r);
        int q = c.getColumnIndexOrThrow("quote_of");
        d.quoteOf = c.isNull(q) ? null : c.getLong(q);
        d.location = c.getString(c.getColumnIndexOrThrow("location"));
        List<String> options = decodePollOptions(c.getString(c.getColumnIndexOrThrow("poll_options")));
        d.pollOptions = options.toArray(new String[0]);
        d.scheduledAt = c.getLong(c.getColumnIndexOrThrow("scheduled_at"));
        d.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return d;
    }

    long createBotAccount(String name, String handle, String bio, String persona, int color, long createdAt) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("handle", normalizeHandle(handle));
        v.put("bio", bio == null ? "" : bio);
        v.put("color", color);
        v.put("verified", 0);
        v.put("private", 0);
        v.put("display_followers", -1);
        v.put("display_following", -1);
        v.put("website", "");
        v.put("location", "");
        v.put("birth_date", "");
        v.put("is_bot", 1);
        v.put("bot_next_at", System.currentTimeMillis());
        v.put("bot_persona", persona == null ? "" : persona);
        v.put("created_at", createdAt > 0 ? createdAt : System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("accounts", null, v);
    }

    List<Account> botAccounts() {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("accounts", null, "is_bot=1", null, null, null, "id ASC");
        try { while (c.moveToNext()) out.add(account(c)); } finally { c.close(); }
        return out;
    }

    List<Account> botsDue(long now, int limit) {
        ArrayList<Account> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("accounts", null, "is_bot=1 AND bot_next_at<=?",
                new String[]{String.valueOf(now)}, null, null, "bot_next_at ASC", String.valueOf(limit));
        try { while (c.moveToNext()) out.add(account(c)); } finally { c.close(); }
        return out;
    }

    void scheduleBot(long accountId, long nextAt) {
        ContentValues v = new ContentValues();
        v.put("bot_next_at", nextAt);
        getWritableDatabase().update("accounts", v, "id=? AND is_bot=1", new String[]{String.valueOf(accountId)});
    }

    int botCount() {
        return (int) DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM accounts WHERE is_bot=1", null);
    }

    void resetEverything() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM scheduled_posts");
            db.execSQL("DELETE FROM poll_votes");
            db.execSQL("DELETE FROM drafts");
            db.execSQL("DELETE FROM messages");
            db.execSQL("DELETE FROM dm_group_members");
            db.execSQL("DELETE FROM dm_groups");
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
