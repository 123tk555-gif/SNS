package com.example.sns.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 通知の JSON(機能定義書 3.7)。post はフォローのとき null。textPreview は本文の先頭 40 文字 */
@Schema(description = "通知。リアルタイム配信(SSE)の `notification` イベントでも、同じ形で届く")
public record NotificationResponse(
        @Schema(description = "通知の ID(大きいほど新しい)", example = "501") long id,
        @Schema(description = "種類。`LIKE`(いいねされた)・`REPLY`(返信された)・`FOLLOW`(フォローされた)・`MENTION`(メンションされた)", example = "LIKE")
        NotificationType type,
        @Schema(description = "操作した人") Actor actor,
        @Schema(description = "対象の投稿。`FOLLOW` のときは `null`", nullable = true) PostPreview post,
        @Schema(description = "既読か", example = "false") boolean read,
        @Schema(description = "通知ができた日時(UTC)", example = "2026-11-01T03:20:00Z") Instant createdAt) {

    @Schema(name = "NotificationActor", description = "通知の、操作した人")
    public record Actor(
            @Schema(description = "ユーザーの ID", example = "9") long id,
            @Schema(description = "ユーザーID(`@` の後ろ)", example = "hanako") String handle,
            @Schema(description = "表示名", example = "はなこ") String displayName,
            @Schema(description = "アイコン画像の URL(`/media/…`)。ないときは `null`", nullable = true) String avatarUrl) {
    }

    @Schema(name = "NotificationPost", description = "通知の対象の投稿の、短い紹介")
    public record PostPreview(
            @Schema(description = "投稿の ID。押すと、その投稿の詳細へ移る", example = "1234") long id,
            @Schema(description = "本文の先頭 40 文字", example = "今日は #SpringBoot の勉強を…") String textPreview) {
    }

    /** 通知の一覧の 1 ページ。nextCursor が null なら、それ以上はない */
    @Schema(name = "NotificationPage", description = "通知の一覧の 1 ページ")
    public record Page(
            @Schema(description = "通知(新しい順)") List<NotificationResponse> items,
            @Schema(description = "続きを読み込むときに、`before` に渡す値。`null` なら、それ以上はない", example = "480", nullable = true)
            Long nextCursor) {
    }
}
