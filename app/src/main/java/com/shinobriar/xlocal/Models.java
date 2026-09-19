package com.shinobriar.xlocal;

final class Account {
    long id;
    String name;
    String handle;
    String bio;
    String avatarPath;
    String bannerPath;
    int color;
    boolean verified;
    boolean isPrivate;
    long displayFollowers;
    long displayFollowing;
}

final class Post {
    long id;
    long authorId;
    String body;
    String mediaPath;
    Long replyTo;
    Long quoteOf;
    long createdAt;
    long likes;
    long reposts;
    long replies;
    long views;
    long bookmarks;
    double viralBoost;
}

final class LocalNotification {
    long id;
    long accountId;
    long actorId;
    Long postId;
    String type;
    long createdAt;
    boolean read;
}

final class DirectMessage {
    long id;
    long senderId;
    long receiverId;
    String body;
    long createdAt;
}
