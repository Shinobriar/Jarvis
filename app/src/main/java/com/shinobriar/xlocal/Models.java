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
    String website;
    String location;
    String birthDate;
    long createdAt;
    boolean isBot;
    long botNextAt;
    String botPersona;
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
    // Profile-timeline metadata. A repost is an event owned by another account
    // while the underlying Post remains authored by authorId.
    boolean profileRepost;
    long profileActorId;
    long profileEventAt;
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


final class DraftPost {
    long id;
    long authorId;
    String body;
    String mediaPath;
    Long replyTo;
    Long quoteOf;
    long createdAt;
}
