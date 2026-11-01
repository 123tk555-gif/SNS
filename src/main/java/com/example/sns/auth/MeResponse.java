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

    static MeResponse from(User user) {
        String avatarUrl = user.getAvatarPath() == null ? null : "/media/" + user.getAvatarPath();
        // 通知は M6 で作る。それまでは 0
        return new MeResponse(user.getId(), user.getEmail(), user.getHandle(), user.getDisplayName(), avatarUrl,
                user.getRole(), user.isOnboarded(), 0);
    }
}
