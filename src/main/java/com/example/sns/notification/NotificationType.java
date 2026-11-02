package com.example.sns.notification;

/** 通知の種類(機能定義書 3.7) */
public enum NotificationType {
    /** 投稿にいいねされた(対象: その投稿) */
    LIKE,
    /** 投稿に返信された(対象: 返信の投稿) */
    REPLY,
    /** フォローされた(対象なし) */
    FOLLOW,
    /** 本文でメンションされた(対象: その投稿) */
    MENTION
}
