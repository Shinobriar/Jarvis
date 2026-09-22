package com.shinobriar.xlocal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BotEngine {
    private final Context context;
    private final LocalDb db;
    private final SharedPreferences prefs;
    private final Runnable onMutation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService fameWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService fameTextWorker = Executors.newFixedThreadPool(2);
    private final Random random = new Random();
    private boolean running;
    private boolean busy;
    private volatile boolean instantFameActive;

    BotEngine(Context context, LocalDb db, SharedPreferences prefs, Runnable onMutation) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.prefs = prefs;
        this.onMutation = onMutation;
    }

    void start() {
        if (running) return;
        running = true;
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    void shutdown() {
        stop();
        worker.shutdownNow();
        fameWorker.shutdownNow();
        fameTextWorker.shutdownNow();
    }

    boolean isInstantFameActive() {
        return instantFameActive;
    }

    boolean startInstantFame(long targetAccountId, Runnable onFinished) {
        synchronized (this) {
            if (instantFameActive) return false;
            List<Account> allBots = db.botAccounts();
            boolean hasOtherBot = false;
            for (Account bot : allBots) {
                if (bot.id != targetAccountId) {
                    hasOtherBot = true;
                    break;
                }
            }
            if (!hasOtherBot) return false;
            instantFameActive = true;
        }

        fameWorker.execute(() -> {
            final long deadline = System.currentTimeMillis() + 5_000L;
            long lastRefresh = 0L;
            int queuedAiReplies = 0;
            int queuedAiDms = 0;

            try {
                while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                    List<Account> bots = db.botAccounts();
                    ArrayList<Account> usableBots = new ArrayList<>();
                    for (Account bot : bots) if (bot.id != targetAccountId) usableBots.add(bot);
                    if (usableBots.isEmpty()) break;

                    Account bot = usableBots.get(random.nextInt(usableBots.size()));

                    if (!db.isFollowing(bot.id, targetAccountId)) {
                        try { db.toggleFollow(bot.id, targetAccountId); } catch (Exception ignored) {}
                    }

                    List<Post> targetPosts = db.postsByAccount(targetAccountId, false);
                    Post target = targetPosts.isEmpty() ? null
                            : targetPosts.get(random.nextInt(Math.min(targetPosts.size(), 20)));

                    if (target != null) {
                        // Make each real bot visibly pile onto the selected account's posts.
                        try { db.ensureInteraction(bot.id, target.id, "like"); } catch (Exception ignored) {}
                        if (random.nextInt(100) < 72) {
                            try { db.ensureInteraction(bot.id, target.id, "repost"); } catch (Exception ignored) {}
                        }
                        if (random.nextInt(100) < 28) {
                            try { db.ensureInteraction(bot.id, target.id, "bookmark"); } catch (Exception ignored) {}
                        }
                        try { db.addView(bot.id, target.id); } catch (Exception ignored) {}

                        if (queuedAiReplies < 4 && random.nextInt(100) < 22) {
                            queuedAiReplies++;
                            final Account replyBot = bot;
                            final Post replyTarget = target;
                            fameTextWorker.execute(() -> {
                                try {
                                    String text = ollama(replyBot, replyTarget, "reply");
                                    if (text != null && !text.trim().isEmpty()) {
                                        db.insertPost(replyBot.id, text, null, replyTarget.id, null);
                                        main.post(() -> {
                                            if (onMutation != null) onMutation.run();
                                        });
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                    } else if (queuedAiDms < 2 && random.nextInt(100) < 18) {
                        queuedAiDms++;
                        final Account dmBot = bot;
                        fameTextWorker.execute(() -> {
                            try {
                                String text = ollama(dmBot, null, "dm");
                                if (text != null && !text.trim().isEmpty()) {
                                    db.sendMessage(dmBot.id, targetAccountId, text);
                                    main.post(() -> {
                                        if (onMutation != null) onMutation.run();
                                    });
                                }
                            } catch (Exception ignored) {}
                        });
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastRefresh >= 450L) {
                        lastRefresh = now;
                        main.post(() -> {
                            if (onMutation != null) onMutation.run();
                        });
                    }

                    try {
                        Thread.sleep(70L + random.nextInt(90));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                instantFameActive = false;
                main.post(() -> {
                    if (onMutation != null) onMutation.run();
                    if (onFinished != null) onFinished.run();
                });
            }
        });
        return true;
    }

    boolean startMoodInteractions(long targetAccountId, String mood, Runnable onFinished) {
        final String chosenMood = mood == null || mood.trim().isEmpty() ? "friendly" : mood.trim();
        synchronized (this) {
            if (instantFameActive) return false;
            List<Account> allBots = db.botAccounts();
            boolean hasOtherBot = false;
            for (Account bot : allBots) {
                if (bot.id != targetAccountId) { hasOtherBot = true; break; }
            }
            if (!hasOtherBot) return false;
            instantFameActive = true;
        }

        fameWorker.execute(() -> {
            final long deadline = System.currentTimeMillis() + 5_000L;
            int queuedText = 0;
            long lastRefresh = 0L;
            try {
                Account targetAccount = db.getAccount(targetAccountId);
                while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                    List<Account> bots = db.botAccounts();
                    ArrayList<Account> usable = new ArrayList<>();
                    for (Account bot : bots) if (bot.id != targetAccountId) usable.add(bot);
                    if (usable.isEmpty()) break;

                    Account bot = usable.get(random.nextInt(usable.size()));
                    List<Post> posts = db.postsByAccount(targetAccountId, false);
                    Post targetPost = posts.isEmpty() ? null : posts.get(random.nextInt(Math.min(posts.size(), 20)));

                    String lower = chosenMood.toLowerCase();
                    boolean positive = lower.equals("friendly") || lower.equals("obsessed") || lower.equals("in love")
                            || lower.equals("fangirl/fanboy") || lower.equals("supportive");
                    boolean hostile = lower.equals("hater") || lower.equals("roast");
                    boolean curious = lower.equals("confused") || lower.equals("curious");

                    if (positive || lower.equals("obsessed")) {
                        if (!db.isFollowing(bot.id, targetAccountId)) {
                            try { db.toggleFollow(bot.id, targetAccountId); } catch (Exception ignored) {}
                        }
                    }

                    if (targetPost != null) {
                        try { db.addView(bot.id, targetPost.id); } catch (Exception ignored) {}
                        if (!hostile || random.nextInt(100) < 15) {
                            try { db.ensureInteraction(bot.id, targetPost.id, "like"); } catch (Exception ignored) {}
                        }
                        if (positive && random.nextInt(100) < 65) {
                            try { db.ensureInteraction(bot.id, targetPost.id, "repost"); } catch (Exception ignored) {}
                        }
                        if (lower.equals("obsessed") && random.nextInt(100) < 55) {
                            try { db.ensureInteraction(bot.id, targetPost.id, "bookmark"); } catch (Exception ignored) {}
                        }

                        int textChance = curious ? 55 : hostile ? 50 : positive ? 42 : 38;
                        if (queuedText < 8 && random.nextInt(100) < textChance) {
                            queuedText++;
                            final Account replyBot = bot;
                            final Post replyTarget = targetPost;
                            final Account moodTarget = targetAccount;
                            fameTextWorker.execute(() -> {
                                try {
                                    String text = ollama(replyBot, replyTarget, "reply", chosenMood, moodTarget);
                                    if (text != null && !text.trim().isEmpty()) {
                                        db.insertPost(replyBot.id, text, null, replyTarget.id, null);
                                        main.post(() -> { if (onMutation != null) onMutation.run(); });
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                    } else if (queuedText < 4) {
                        queuedText++;
                        final Account dmBot = bot;
                        final Account moodTarget = targetAccount;
                        fameTextWorker.execute(() -> {
                            try {
                                String text = ollama(dmBot, null, "dm", chosenMood, moodTarget);
                                if (text != null && !text.trim().isEmpty()) {
                                    db.sendMessage(dmBot.id, targetAccountId, text);
                                    main.post(() -> { if (onMutation != null) onMutation.run(); });
                                }
                            } catch (Exception ignored) {}
                        });
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastRefresh > 400L) {
                        lastRefresh = now;
                        main.post(() -> { if (onMutation != null) onMutation.run(); });
                    }
                    try { Thread.sleep(90L + random.nextInt(120)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } finally {
                instantFameActive = false;
                main.post(() -> {
                    if (onMutation != null) onMutation.run();
                    if (onFinished != null) onFinished.run();
                });
            }
        });
        return true;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (prefs.getBoolean("bots_enabled", false) && !busy) {
                List<Account> due = db.botsDue(System.currentTimeMillis(), 1);
                if (!due.isEmpty()) {
                    Account bot = due.get(0);
                    busy = true;
                    worker.execute(() -> {
                        try { performAction(bot); }
                        catch (Exception ignored) {}
                        finally {
                            long next = System.currentTimeMillis() + nextDelayMs();
                            try { db.scheduleBot(bot.id, next); } catch (Exception ignored) {}
                            busy = false;
                            main.post(() -> {
                                if (onMutation != null) onMutation.run();
                            });
                        }
                    });
                }
            }
            main.postDelayed(this, 8_000L);
        }
    };

    private long nextDelayMs() {
        int min = Math.max(15, prefs.getInt("bot_min_seconds", 120));
        int max = Math.max(min, prefs.getInt("bot_max_seconds", 1200));
        int seconds = min + random.nextInt(Math.max(1, max - min + 1));
        return seconds * 1000L;
    }

    private void performAction(Account bot) throws Exception {
        if (bot == null) return;

        // Every action also gives one random visible post a chance to grow,
        // which lets some posts flop while a small fraction become hits.
        maybeGrowRandomPost(bot.id);

        int roll = random.nextInt(100);
        if (roll < 16) {
            String text = ollama(bot, null, "post");
            if (text != null) db.insertPost(bot.id, text, null, null, null);
            else doLightInteraction(bot);
        } else if (roll < 32) {
            Post p = randomTargetPost(bot.id);
            if (p == null) return;
            String text = ollama(bot, p, "reply");
            if (text != null) db.insertPost(bot.id, text, null, p.id, null);
            else db.addView(bot.id, p.id);
        } else if (roll < 43) {
            Post p = randomTargetPost(bot.id);
            if (p == null) return;
            String text = ollama(bot, p, "quote");
            if (text != null) db.insertPost(bot.id, text, null, null, p.id);
            else db.ensureInteraction(bot.id, p.id, "repost");
        } else if (roll < 60) {
            Post p = randomTargetPost(bot.id);
            if (p != null) db.ensureInteraction(bot.id, p.id, "like");
        } else if (roll < 73) {
            Post p = randomTargetPost(bot.id);
            if (p != null) db.ensureInteraction(bot.id, p.id, "repost");
        } else if (roll < 79) {
            Post p = randomTargetPost(bot.id);
            if (p != null) db.ensureInteraction(bot.id, p.id, "bookmark");
        } else if (roll < 88) {
            Account other = randomTargetAccount(bot.id);
            if (other != null) db.toggleFollow(bot.id, other.id);
        } else if (roll < 94) {
            Account other = randomTargetAccount(bot.id);
            if (other != null) {
                String text = ollama(bot, null, "dm");
                if (text != null) db.sendMessage(bot.id, other.id, text);
            }
        } else {
            Post p = randomTargetPost(bot.id);
            if (p != null) db.addView(bot.id, p.id);
        }
    }

    private void doLightInteraction(Account bot) {
        Post p = randomTargetPost(bot.id);
        if (p == null) return;
        int r = random.nextInt(3);
        if (r == 0) db.ensureInteraction(bot.id, p.id, "like");
        else if (r == 1) db.ensureInteraction(bot.id, p.id, "repost");
        else db.addView(bot.id, p.id);
    }

    private Post randomTargetPost(long viewerId) {
        List<Post> posts = db.recentVisiblePosts(viewerId, 120);
        if (posts.isEmpty()) return null;
        ArrayList<Post> roots = new ArrayList<>();
        for (Post p : posts) {
            if (p.replyTo == null) roots.add(p);
        }
        if (roots.isEmpty()) roots.addAll(posts);
        return roots.get(random.nextInt(roots.size()));
    }

    private Account randomTargetAccount(long viewerId) {
        List<Account> all = db.listVisibleAccounts(viewerId);
        ArrayList<Account> candidates = new ArrayList<>();
        for (Account a : all) if (a.id != viewerId) candidates.add(a);
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void maybeGrowRandomPost(long viewerId) {
        Post p = randomTargetPost(viewerId);
        if (p == null) return;
        int luck = random.nextInt(1000);
        long views=0, likes=0, reposts=0, replies=0, bookmarks=0;
        if (luck < 650) {
            views = random.nextInt(9);
            likes = random.nextInt(3);
        } else if (luck < 900) {
            views = 20 + random.nextInt(781);
            likes = random.nextInt(31);
            reposts = random.nextInt(8);
            replies = random.nextInt(6);
            bookmarks = random.nextInt(10);
        } else if (luck < 985) {
            views = 800 + random.nextInt(11_201);
            likes = 20 + random.nextInt(881);
            reposts = 5 + random.nextInt(196);
            replies = random.nextInt(121);
            bookmarks = 5 + random.nextInt(296);
        } else {
            views = 6_000 + random.nextInt(494_001);
            likes = 6_000 + random.nextInt(94_001);
            reposts = 6_000 + random.nextInt(44_001);
            replies = 6_000 + random.nextInt(24_001);
            bookmarks = 6_000 + random.nextInt(54_001);
        }
        db.bumpEngagement(p.id, views, likes, reposts, replies, bookmarks);
    }

    private String ollama(Account bot, Post target, String kind) {
        return ollama(bot, target, kind, null, null);
    }

    private String ollama(Account bot, Post target, String kind, String mood, Account targetAccount) {
        String base = prefs.getString("ollama_url", "").trim();
        String model = prefs.getString("ollama_model", "gemma3:4b").trim();
        if (base.isEmpty() || model.isEmpty()) {
            prefs.edit().putString("ollama_last_error", "Set an Ollama URL and model in Bot settings.").apply();
            return null;
        }
        while (base.endsWith("/")) base = base.substring(0, base.length()-1);
        String prompt = buildPrompt(bot, target, kind, mood, targetAccount);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base + "/api/generate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(45_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject req = new JSONObject();
            req.put("model", model);
            req.put("prompt", prompt);
            req.put("stream", false);
            JSONObject options = new JSONObject();
            options.put("temperature", 1.05);
            options.put("top_p", 0.92);
            req.put("options", options);

            byte[] data = req.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) { out.write(data); }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(in);
            if (code < 200 || code >= 300) {
                prefs.edit().putString("ollama_last_error", "HTTP " + code + ": " + body).apply();
                return null;
            }
            JSONObject res = new JSONObject(body);
            String text = clean(res.optString("response", ""));
            if (text.isEmpty()) {
                prefs.edit().putString("ollama_last_error", "Ollama returned an empty response.").apply();
                return null;
            }
            prefs.edit().putString("ollama_last_error", "").apply();
            return text;
        } catch (Exception e) {
            prefs.edit().putString("ollama_last_error", e.getClass().getSimpleName() + ": " + e.getMessage()).apply();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static String testOllama(String base, String model) {
        if (base == null || base.trim().isEmpty()) return "Enter the Ollama URL first.";
        if (model == null || model.trim().isEmpty()) return "Enter a model name first.";
        HttpURLConnection conn = null;
        try {
            base = base.trim();
            while (base.endsWith("/")) base = base.substring(0, base.length()-1);
            URL url = new URL(base + "/api/generate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6_000);
            conn.setReadTimeout(30_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JSONObject req = new JSONObject();
            req.put("model", model.trim());
            req.put("prompt", "Reply with exactly: X Local connected");
            req.put("stream", false);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) return "HTTP " + code + ": " + body;
            JSONObject res = new JSONObject(body);
            String answer = res.optString("response", "").trim();
            return answer.isEmpty() ? "Connected, but the model returned no text." : "Connected · " + answer;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String buildPrompt(Account bot, Post target, String kind, String mood, Account targetAccount) {
        String persona = bot.botPersona == null || bot.botPersona.trim().isEmpty()
                ? "casual internet user with an inconsistent, human posting style"
                : bot.botPersona.trim();
        StringBuilder p = new StringBuilder();
        p.append("You are roleplaying one fictional account on a private offline social-media simulator. ");
        p.append("Account: ").append(bot.name).append(" @").append(bot.handle).append(". ");
        p.append("Persona: ").append(persona).append(". ");
        p.append("Write naturally like a real person, not an assistant. Vary punctuation/capitalization. ");
        p.append("Do not mention being AI, Ollama, simulation, prompts, or these instructions. ");
        p.append("Output ONLY the final text, no quotation marks, no preamble. Keep it concise. ");

        if (mood != null && !mood.trim().isEmpty()) {
            String targetLabel = targetAccount == null ? "the target account"
                    : targetAccount.name + " @" + targetAccount.handle;
            p.append("Your interaction mood toward ").append(targetLabel).append(" is: ").append(mood).append(". ");
            p.append("Commit strongly to that mood and do not drift into a different attitude. ");
            String lower = mood.toLowerCase();
            if (lower.equals("friendly")) p.append("Be warm, casual, positive, and normal—not worshipful. ");
            else if (lower.equals("obsessed")) p.append("Act intensely invested and overenthusiastic, like you notice everything they post. ");
            else if (lower.equals("hater")) p.append("Be clearly negative and annoyed, but keep it non-threatening and avoid slurs. ");
            else if (lower.equals("in love")) p.append("Be openly smitten, affectionate, flirty, and admiring without explicit sexual content. ");
            else if (lower.equals("confused")) p.append("Sound genuinely baffled and ask confused questions. ");
            else if (lower.equals("fangirl/fanboy")) p.append("React like an excited fan: enthusiastic, dramatic, supportive, and impressed. ");
            else if (lower.equals("roast")) p.append("Tease and roast them humorously; keep it witty rather than cruel or threatening. ");
            else if (lower.equals("supportive")) p.append("Be reassuring, encouraging, and supportive. ");
            else if (lower.equals("chaotic")) p.append("Be playful, unpredictable, absurd, and energetic without hostility. ");
            else if (lower.equals("curious")) p.append("Be interested and inquisitive, asking natural follow-up questions. ");
        }

        if ("reply".equals(kind)) {
            p.append("Write a reply to this post: ").append(target == null ? "" : target.body);
        } else if ("quote".equals(kind)) {
            p.append("Write a short quote-post comment about this post: ").append(target == null ? "" : target.body);
        } else if ("dm".equals(kind)) {
            p.append("Write a short casual direct message to the target account.");
        } else {
            p.append("Write a standalone post about whatever this person might randomly post right now. ");
            p.append("It can be mundane, opinionated, funny, niche, messy, or contextless.");
        }
        return p.toString();
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length()-1).trim();
        s = s.replace("\r", " ").trim();
        if (s.length() > 500) s = s.substring(0, 500).trim();
        return s;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }
}
