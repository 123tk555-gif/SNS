package com.example.sns.post;

import java.time.Instant;
import java.util.List;

/** 投稿の JSON(機能定義書 1.7)。投稿を返す API は、すべてこの形を使う。 */
public record PostResponse(
        long id,
        Author author,
        String text,
        List<Image> images,
        ReplyTo replyTo,
        long likeCount,
        long replyCount,
        boolean likedByMe,
        boolean bookmarkedByMe,
        boolean mine,
        boolean hidden,
        Instant createdAt,
        Instant editedAt,
        boolean unavailable) {

    public record Author(long id, String handle, String displayName, String avatarUrl) {
    }

    public record Image(String url, String alt, int width, int height) {
    }

    public record ReplyTo(long id, String authorHandle, boolean unavailable) {
    }

    /** 削除・非表示などで見られない投稿。id 以外は空にする */
    static PostResponse unavailable(long id) {
        return new PostResponse(id, null, "", List.of(), null, 0, 0, false, false, false, false, null, null, true);
    }
}
