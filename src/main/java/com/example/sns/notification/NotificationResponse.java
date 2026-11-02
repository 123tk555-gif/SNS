package com.example.sns.notification;

import java.time.Instant;
import java.util.List;

/** 通知の JSON(機能定義書 3.7)。post はフォローのとき null。textPreview は本文の先頭 40 文字 */
public record NotificationResponse(
        long id,
        NotificationType type,
        Actor actor,
        PostPreview post,
        boolean read,
        Instant createdAt) {

    public record Actor(long id, String handle, String displayName, String avatarUrl) {
    }

    public record PostPreview(long id, String textPreview) {
    }

    /** 通知の一覧の 1 ページ。nextCursor が null なら、それ以上はない */
    public record Page(List<NotificationResponse> items, Long nextCursor) {
    }
}
