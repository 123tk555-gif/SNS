package com.example.sns.profile;

import java.util.List;

/** 一覧用のユーザー(機能定義書 1.8)。followedByMe: 見る人がフォローしている / followsMe: 見る人をフォローしている */
public record UserSummary(
        long id,
        String handle,
        String displayName,
        String avatarUrl,
        String bio,
        boolean followedByMe,
        boolean followsMe) {

    /** ユーザーの一覧の 1 ページ。nextCursor が null なら、それ以上はない */
    public record Page(List<UserSummary> items, Long nextCursor) {
    }
}
