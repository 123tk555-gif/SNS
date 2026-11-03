package com.example.sns.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 一覧用のユーザー(機能定義書 1.8)。followedByMe: 見る人がフォローしている / followsMe: 見る人をフォローしている */
@Schema(name = "UserSummary", description = "一覧用のユーザー(検索結果・フォロー中・フォロワー・ブロック・ミュートの一覧など)")
public record UserSummary(
        @Schema(description = "ユーザーの ID", example = "9") long id,
        @Schema(description = "ユーザーID(`@` の後ろ)", example = "hanako") String handle,
        @Schema(description = "表示名", example = "はなこ") String displayName,
        @Schema(description = "アイコン画像の URL(`/media/…`)。ないときは `null`", nullable = true) String avatarUrl,
        @Schema(description = "自己紹介。未設定のときは `null`", example = "Web デザインとカフェめぐりが好きです", nullable = true) String bio,
        @Schema(description = "自分がフォローしているか", example = "true") boolean followedByMe,
        @Schema(description = "相手が自分をフォローしているか", example = "false") boolean followsMe) {

    /** ユーザーの一覧の 1 ページ。nextCursor が null なら、それ以上はない */
    @Schema(name = "UserPage", description = "ユーザーの一覧の 1 ページ")
    public record Page(
            @Schema(description = "ユーザー(この API の並び順どおり)") List<UserSummary> items,
            @Schema(description = "続きを読み込むときに、`before` に渡す値。`null` なら、それ以上はない", example = "40", nullable = true)
            Long nextCursor) {
    }
}
