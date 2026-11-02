package com.example.sns.profile;

import java.time.Instant;

/**
 * プロフィール(GET /api/users/{handle}。機能定義書 3.2)。
 * blockedByMe・mutedByMe は M8(ブロック・ミュート)で使う。それまでは false
 */
public record ProfileResponse(
        long id,
        String handle,
        String displayName,
        String bio,
        String avatarUrl,
        String headerUrl,
        long followingCount,
        long followerCount,
        long postCount,
        Instant joinedAt,
        boolean followedByMe,
        boolean followsMe,
        boolean blockedByMe,
        boolean mutedByMe,
        boolean me) {
}
