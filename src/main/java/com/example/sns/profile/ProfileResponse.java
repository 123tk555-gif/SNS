package com.example.sns.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * プロフィール(GET /api/users/{handle}。機能定義書 3.2)。
 * blockedByMe・mutedByMe は M8(ブロック・ミュート)で使う。それまでは false
 */
@Schema(description = "プロフィール")
public record ProfileResponse(
        @Schema(description = "ユーザーの ID", example = "7") long id,
        @Schema(description = "ユーザーID(`@` の後ろ)", example = "taro_dev") String handle,
        @Schema(description = "表示名", example = "たろう") String displayName,
        @Schema(description = "自己紹介(160 文字以内)。未設定のときは `null`", example = "Java を勉強中です", nullable = true) String bio,
        @Schema(description = "アイコン画像の URL(`/media/…`)。ないときは `null`", example = "/media/avatars/3f9c1a7e-0b1d-4c55-9e0a-2b6f4c7d8e91.webp", nullable = true)
        String avatarUrl,
        @Schema(description = "ヘッダー画像の URL(`/media/…`)。ないときは `null`", nullable = true) String headerUrl,
        @Schema(description = "フォローしている人の数", example = "12") long followingCount,
        @Schema(description = "フォロワーの数", example = "30") long followerCount,
        @Schema(description = "投稿の数(返信を含む。削除したものは数えない)", example = "85") long postCount,
        @Schema(description = "登録した日時(UTC)", example = "2026-10-25T05:00:00Z") Instant joinedAt,
        @Schema(description = "自分がフォローしているか", example = "false") boolean followedByMe,
        @Schema(description = "相手が自分をフォローしているか", example = "true") boolean followsMe,
        @Schema(description = "自分がブロックしているか", example = "false") boolean blockedByMe,
        @Schema(description = "自分がミュートしているか", example = "false") boolean mutedByMe,
        @Schema(description = "自分自身のプロフィールか", example = "false") boolean me) {
}
