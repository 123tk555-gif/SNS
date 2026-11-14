package com.example.sns.auth;

import com.example.sns.user.Role;
import com.example.sns.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** ログイン中のユーザー(GET /api/auth/me など。機能定義書 3.1) */
@Schema(description = "ログイン中のユーザー。初期設定の前は `handle`・`displayName` が `null`、`onboarded` が `false`")
public record MeResponse(
        @Schema(description = "ユーザーの ID(1 から増えていく整数)", example = "7") long id,
        @Schema(description = "メールアドレス(小文字)", example = "taro@example.com") String email,
        @Schema(description = "ユーザーID(`@` の後ろ)。初期設定の前は `null`", example = "taro_dev", nullable = true) String handle,
        @Schema(description = "表示名。初期設定の前は `null`", example = "たろう", nullable = true) String displayName,
        @Schema(description = "アイコン画像の URL(`/media/…`)。ないときは `null`", example = "/media/avatars/3f9c1a7e-0b1d-4c55-9e0a-2b6f4c7d8e91.webp", nullable = true)
        String avatarUrl,
        @Schema(description = "権限。`USER`(一般)か `ADMIN`(管理者)", example = "USER") Role role,
        @Schema(description = "初期設定(ユーザーIDと表示名の決定)が済んでいるか", example = "true") boolean onboarded,
        @Schema(description = "未読の通知の数", example = "3") long unreadNotificationCount,
        @Schema(description = "お試しログインのアカウントか(`true` なら、プロフィールを変えられず、書き込みは毎日消える)", example = "false")
        boolean guest) {

    static MeResponse from(User user, long unreadNotificationCount) {
        String avatarUrl = user.getAvatarPath() == null ? null : "/media/" + user.getAvatarPath();
        return new MeResponse(user.getId(), user.getEmail(), user.getHandle(), user.getDisplayName(), avatarUrl,
                user.getRole(), user.isOnboarded(), unreadNotificationCount, user.isGuest());
    }
}
