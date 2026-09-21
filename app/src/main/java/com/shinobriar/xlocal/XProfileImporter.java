package com.shinobriar.xlocal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class XProfileImporter {
    private static final String API_URL = "https://api.x.com/2/users/by/username/";
    private static final String PUBLIC_INFO_URL =
            "https://cdn.syndication.twimg.com/widgets/followbutton/info.json?screen_names=";
    private static final String SECURE_PREFS = "xlocal_secure_import";
    private static final String TOKEN_PREF = "x_bearer_token";
    private static final String KEY_ALIAS = "xlocal_x_importer_key";
    private static final Pattern PROFILE_URL = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?(?:x|twitter)\\.com/([A-Za-z0-9_]{1,15})(?:[/?#].*)?");
    private static final Pattern HANDLE = Pattern.compile("^@?([A-Za-z0-9_]{1,15})$");

    static final class Result {
        String source;
        String name;
        String handle;
        String bio = "";
        String location = "";
        String website = "";
        String avatarUrl;
        String bannerUrl;
        String avatarPath;
        String bannerPath;
        long createdAt;
        long followers = -1;
        long following = -1;
        boolean verified;
        boolean isPrivate;
        boolean partial;
        String warning = "";
    }

    private XProfileImporter() {}

    static String extractHandle(String input) {
        if (input == null) return null;
        String value = input.trim();
        Matcher direct = HANDLE.matcher(value);
        if (direct.matches()) return direct.group(1);
        Matcher url = PROFILE_URL.matcher(value);
        if (!url.find()) {
            url = PROFILE_URL.matcher(value.replace("mobile.", "www."));
            if (!url.find()) return null;
        }
        String handle = url.group(1);
        String lower = handle.toLowerCase(Locale.US);
        if (lower.equals("home") || lower.equals("explore") || lower.equals("search")
                || lower.equals("messages") || lower.equals("notifications")
                || lower.equals("settings") || lower.equals("compose") || lower.equals("i")) {
            return null;
        }
        return handle;
    }

    static Result importProfile(Context context, String handle, String bearerToken) throws Exception {
        Result result = null;
        String officialFailure = "";
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            try {
                result = fromOfficialApi(handle, bearerToken.trim());
            } catch (Exception e) {
                officialFailure = safeMessage(e);
            }
        }
        if (result == null) {
            try {
                result = fromPublicInfo(handle);
            } catch (Exception ignored) {}
        }
        if (result == null) {
            try {
                result = fromPublicPage(handle);
            } catch (Exception ignored) {}
        }
        if (result == null) {
            String suffix = officialFailure.isEmpty() ? "" : " The configured API token also failed: " + officialFailure;
            throw new Exception("X did not return a public profile for @" + handle + "." + suffix);
        }

        if (!officialFailure.isEmpty()) {
            result.warning = "The official API token failed, so public profile data was used instead: " + officialFailure;
        }
        if (result.handle == null || result.handle.trim().isEmpty()) result.handle = handle;
        if (result.name == null || result.name.trim().isEmpty()) result.name = result.handle;

        if (result.avatarUrl != null && !result.avatarUrl.trim().isEmpty()) {
            try {
                String upgraded = result.avatarUrl.replace("_normal.", "_400x400.");
                try {
                    result.avatarPath = downloadImage(context, upgraded, "avatar");
                } catch (Exception e) {
                    result.avatarPath = downloadImage(context, result.avatarUrl, "avatar");
                }
            } catch (Exception e) {
                result.warning = appendWarning(result.warning, "The profile picture could not be downloaded.");
            }
        }
        if (result.bannerUrl != null && !result.bannerUrl.trim().isEmpty()) {
            try {
                String highResolution = result.bannerUrl.endsWith("/")
                        ? result.bannerUrl + "1500x500" : result.bannerUrl + "/1500x500";
                try {
                    result.bannerPath = downloadImage(context, highResolution, "banner");
                } catch (Exception e) {
                    result.bannerPath = downloadImage(context, result.bannerUrl, "banner");
                }
            } catch (Exception e) {
                result.warning = appendWarning(result.warning, "The header image could not be downloaded.");
            }
        }
        return result;
    }

    private static Result fromOfficialApi(String handle, String token) throws Exception {
        String fields = "created_at,description,entities,location,name,profile_banner_url," +
                "profile_image_url,protected,public_metrics,url,username,verified";
        String endpoint = API_URL + URLEncoder.encode(handle, "UTF-8") + "?user.fields=" + fields;
        JSONObject root = new JSONObject(httpText(endpoint, token, "application/json"));
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            String detail = root.optString("detail", root.optString("title", "Profile lookup failed"));
            throw new Exception(detail);
        }
        Result result = new Result();
        result.source = "Official X API";
        result.name = data.optString("name", handle);
        result.handle = data.optString("username", handle);
        result.bio = data.optString("description", "");
        result.location = data.optString("location", "");
        result.website = expandedWebsite(data);
        result.avatarUrl = data.optString("profile_image_url", null);
        result.bannerUrl = data.optString("profile_banner_url", null);
        result.createdAt = parseDate(data.optString("created_at", ""));
        result.verified = data.optBoolean("verified", false);
        result.isPrivate = data.optBoolean("protected", false);
        JSONObject metrics = data.optJSONObject("public_metrics");
        if (metrics != null) {
            result.followers = metrics.optLong("followers_count", -1);
            result.following = metrics.optLong("following_count", -1);
        }
        return result;
    }

    private static Result fromPublicInfo(String handle) throws Exception {
        String endpoint = PUBLIC_INFO_URL + URLEncoder.encode(handle, "UTF-8");
        JSONArray array = new JSONArray(httpText(endpoint, null, "application/json"));
        if (array.length() == 0) throw new Exception("No public profile returned");
        JSONObject data = array.getJSONObject(0);
        Result result = new Result();
        result.source = "Public X profile";
        result.name = data.optString("name", handle);
        result.handle = data.optString("screen_name", handle);
        result.bio = data.optString("description", "");
        result.location = data.optString("location", "");
        result.website = data.optString("url", "");
        result.avatarUrl = firstNonEmpty(data.optString("profile_image_url_https", null),
                data.optString("profile_image_url", null));
        result.bannerUrl = data.optString("profile_banner_url", null);
        result.createdAt = parseDate(data.optString("created_at", ""));
        result.followers = data.has("followers_count") ? data.optLong("followers_count", -1) : -1;
        result.following = data.has("friends_count") ? data.optLong("friends_count", -1) : -1;
        result.verified = data.optBoolean("verified", false);
        result.isPrivate = data.optBoolean("protected", false);
        result.partial = result.bannerUrl == null || result.bannerUrl.trim().isEmpty();
        return result;
    }

    private static Result fromPublicPage(String handle) throws Exception {
        String html = httpText("https://x.com/" + URLEncoder.encode(handle, "UTF-8"),
                null, "text/html,application/xhtml+xml");
        String title = metaContent(html, "og:title", "twitter:title");
        String description = metaContent(html, "og:description", "twitter:description", "description");
        String image = metaContent(html, "og:image", "twitter:image");
        if (title.isEmpty() && description.isEmpty() && image.isEmpty()) {
            throw new Exception("The public X page did not include profile metadata");
        }
        Result result = new Result();
        result.source = "Public X page (limited)";
        result.handle = handle;
        result.name = title;
        int marker = result.name.indexOf(" (@");
        if (marker > 0) result.name = result.name.substring(0, marker).trim();
        result.name = result.name.replace(" / X", "").trim();
        result.bio = description;
        result.avatarUrl = image.isEmpty() ? null : image;
        result.partial = true;
        result.warning = "X exposed only limited public-page metadata. Review the fields before importing.";
        return result;
    }

    private static String expandedWebsite(JSONObject data) {
        JSONObject entities = data.optJSONObject("entities");
        if (entities != null) {
            JSONObject url = entities.optJSONObject("url");
            JSONArray urls = url == null ? null : url.optJSONArray("urls");
            if (urls != null && urls.length() > 0) {
                JSONObject first = urls.optJSONObject(0);
                if (first != null) {
                    String expanded = first.optString("expanded_url", "");
                    if (!expanded.isEmpty()) return expanded;
                }
            }
        }
        return data.optString("url", "");
    }

    private static String httpText(String address, String token, String accept) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) XLocal/1.0");
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readText(stream, 4 * 1024 * 1024);
            if (code < 200 || code >= 300) {
                throw new Exception("X returned HTTP " + code + (body.isEmpty() ? "" : ": " + compact(body)));
            }
            return body;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String downloadImage(Context context, String address, String prefix) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) XLocal/1.0");
            connection.setRequestProperty("Accept", "image/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Image HTTP " + code);
            String type = connection.getContentType();
            String extension = type != null && type.toLowerCase(Locale.US).contains("png") ? ".png"
                    : type != null && type.toLowerCase(Locale.US).contains("webp") ? ".webp" : ".jpg";
            File directory = new File(context.getFilesDir(), "media");
            if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create media folder");
            File output = new File(directory, "x-import-" + prefix + "-" + UUID.randomUUID() + extension);
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                byte[] buffer = new byte[32 * 1024];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) > 0) {
                    total += read;
                    if (total > 20 * 1024 * 1024) throw new Exception("Image is too large");
                    out.write(buffer, 0, read);
                }
                if (total == 0) throw new Exception("Image was empty");
            } catch (Exception e) {
                output.delete();
                throw e;
            }
            return output.getAbsolutePath();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void saveBearerToken(Context context, String token) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            clearBearerToken(context);
            return;
        }
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
        String value = android.util.Base64.encodeToString(cipher.getIV(), android.util.Base64.NO_WRAP)
                + ":" + android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP);
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(TOKEN_PREF, value).apply();
    }

    static String loadBearerToken(Context context) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE);
            String stored = preferences.getString(TOKEN_PREF, "");
            if (stored == null || stored.isEmpty() || !stored.contains(":")) return "";
            String[] parts = stored.split(":", 2);
            byte[] iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP);
            byte[] encrypted = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    static void clearBearerToken(Context context) {
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
                .edit().remove(TOKEN_PREF).apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "EEE MMM dd HH:mm:ss Z yyyy"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(value);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static String metaContent(String html, String... names) {
        Matcher matcher = Pattern.compile("(?is)<meta\\s+[^>]*>").matcher(html == null ? "" : html);
        while (matcher.find()) {
            String tag = matcher.group();
            String key = attribute(tag, "property");
            if (key.isEmpty()) key = attribute(tag, "name");
            for (String name : names) {
                if (name.equalsIgnoreCase(key)) return decodeHtml(attribute(tag, "content"));
            }
        }
        return "";
    }

    private static String attribute(String tag, String attribute) {
        Matcher matcher = Pattern.compile("(?is)\\b" + Pattern.quote(attribute)
                + "\\s*=\\s*([\\\"'])(.*?)\\1").matcher(tag);
        return matcher.find() ? matcher.group(2) : "";
    }

    private static String decodeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        }
        return Html.fromHtml(value).toString().trim();
    }

    private static String readText(InputStream input, int limit) throws Exception {
        if (input == null) return "";
        try (InputStream in = new BufferedInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > limit) throw new Exception("X response was too large");
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static String compact(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private static String appendWarning(String existing, String next) {
        return existing == null || existing.trim().isEmpty() ? next : existing + " " + next;
    }

    private static String safeMessage(Exception error) {
        return error == null || error.getMessage() == null ? "unknown error" : compact(error.getMessage());
    }
}
