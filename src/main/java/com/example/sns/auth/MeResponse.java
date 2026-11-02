package com.example.sns.auth;

import com.example.sns.user.Role;
import com.example.sns.user.User;

/** ログイン中のユーザー(GET /api/auth/me など。機能定義書 3.1) */
public record MeResponse(
        long id,
        String email,
        String handle,
        String displayName,
        String avatarUrl,
        Role role,
        boolean onboarded,
        long unreadNotificationCount) {

    static MeResponse from(User user, long unreadNotificationCount) {
        String avatarUrl = user.getAvatarPath() == null ? null : "/media/" + user.getAvatarPath();
        return new MeResponse(user.getId(), user.getEmail(), user.getHandle(), user.getDisplayName(), avatarUrl,
                user.getRole(), user.isOnboarded(), unreadNotificationCount);
    }
}
