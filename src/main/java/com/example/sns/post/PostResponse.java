package com.example.sns.post;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 投稿の JSON(機能定義書 1.7)。投稿を返す API は、すべてこの形を使う。 */
@Schema(description = "投稿。投稿を返す API は、すべてこの形を使う。見られない投稿(削除・非表示・投稿者が利用停止中)は、`unavailable` が `true` で、`id` 以外が空になる")
public record PostResponse(
        @Schema(description = "投稿の ID(1 から増えていく整数。大きいほど新しい)", example = "1234") long id,
        @Schema(description = "投稿者。見られない投稿では `null`", nullable = true) Author author,
        @Schema(description = "本文(改行を含む)。画像だけの投稿は空文字。ハッシュタグ・メンション・URL の抜き出しは、画面が行う", example = "新しいカフェを見つけました #カフェ巡り @hanako")
        String text,
        @Schema(description = "画像(0〜10 枚。投稿した順)") List<Image> images,
        @Schema(description = "返信のときの、返信先。返信でなければ `null`", nullable = true) ReplyTo replyTo,
        @Schema(description = "いいねの数", example = "5") long likeCount,
        @Schema(description = "返信の数(見えない返信は数えない)", example = "2") long replyCount,
        @Schema(description = "自分がいいねしているか", example = "false") boolean likedByMe,
        @Schema(description = "自分がブックマークしているか", example = "false") boolean bookmarkedByMe,
        @Schema(description = "自分の投稿か(編集・削除のメニューを出すかの判断に使う)", example = "false") boolean mine,
        @Schema(description = "管理者に非表示にされているか。`true` の投稿が見えるのは、投稿者本人のプロフィールだけ", example = "false") boolean hidden,
        @Schema(description = "投稿した日時(UTC。ISO 8601)", example = "2026-11-01T03:15:00Z", nullable = true) Instant createdAt,
        @Schema(description = "最後に編集した日時(UTC)。編集していなければ `null`", example = "2026-11-01T03:20:00Z", nullable = true) Instant editedAt,
        @Schema(description = "見られない投稿か(削除・管理者による非表示・投稿者の利用停止)。`true` のときは、`id` 以外は空", example = "false")
        boolean unavailable) {

    @Schema(name = "PostAuthor", description = "投稿者")
    public record Author(
            @Schema(description = "ユーザーの ID", example = "7") long id,
            @Schema(description = "ユーザーID(`@` の後ろ)", example = "taro_dev") String handle,
            @Schema(description = "表示名", example = "たろう") String displayName,
            @Schema(description = "アイコン画像の URL(`/media/…`)。ないときは `null`", example = "/media/avatars/3f9c1a7e-0b1d-4c55-9e0a-2b6f4c7d8e91.webp", nullable = true)
            String avatarUrl) {
    }

    @Schema(name = "PostImage", description = "投稿の画像")
    public record Image(
            @Schema(description = "画像の URL(`/media/…`。ログイン中の人だけが取れる)", example = "/media/posts/2026/11/a1b2c3d4-0b1d-4c55-9e0a-2b6f4c7d8e91.webp") String url,
            @Schema(description = "代替テキスト(読み上げ用の説明)。ないときは空文字", example = "ノートパソコンの画面") String alt,
            @Schema(description = "画像の幅(ピクセル)", example = "1600") int width,
            @Schema(description = "画像の高さ(ピクセル)", example = "1200") int height) {
    }

    @Schema(name = "PostReplyTo", description = "返信先")
    public record ReplyTo(
            @Schema(description = "返信先の投稿の ID", example = "1200") long id,
            @Schema(description = "返信先の投稿者のユーザーID。返信先が見られないときは `null`", example = "hanako", nullable = true) String authorHandle,
            @Schema(description = "返信先が見られない(削除・非表示など)か。`true` のとき、画面には「削除された投稿への返信」と出す", example = "false") boolean unavailable) {
    }

    /** 削除・非表示などで見られない投稿。id 以外は空にする */
    static PostResponse unavailable(long id) {
        return new PostResponse(id, null, "", List.of(), null, 0, 0, false, false, false, false, null, null, true);
    }
}
