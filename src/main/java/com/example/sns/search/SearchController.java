package com.example.sns.search;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.hashtag.HashtagService;
import com.example.sns.post.PostResponse;
import com.example.sns.post.PostService;
import com.example.sns.profile.ProfileService;
import com.example.sns.profile.UserSummary;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 検索・トレンド・ハッシュタグの投稿一覧の API(機能定義書 3.9・3.10)。 */
@RestController
@Tag(name = ApiTags.SEARCH)
class SearchController {

    static final int MAX_QUERY = 100;
    /** 一度に探せる言葉の数(多すぎる言葉で、データベースに重い問い合わせをさせないため) */
    static final int MAX_WORDS = 10;
    static final String EMPTY_QUERY = "検索する言葉を入力してください";
    static final String QUERY_TOO_LONG = "100文字以内で入力してください";

    private static final String Q = "検索する言葉。前後の空白を取り除いて 1〜100 文字。大文字・小文字、全角・半角の英数字は区別しない";

    @Schema(description = "トレンド")
    record Trends(
            @Schema(description = "直近 24 時間によく使われたハッシュタグ(上位 10 件)") List<HashtagService.TagCount> tags,
            @Schema(description = "直近 24 時間の人気投稿(いいねが 1 件以上の、返信以外の投稿。上位 10 件)") List<PostResponse> popularPosts) {
    }

    @Schema(description = "ユーザーの検索結果")
    record UserResults(
            @Schema(description = "ユーザー(最大 20 件)") List<UserSummary> items) {
    }

    @Schema(description = "ハッシュタグの検索結果")
    record TagResults(
            @Schema(description = "ハッシュタグ(最大 20 件)") List<HashtagService.TagCount> items) {
    }

    private final PostService posts;
    private final ProfileService profiles;
    private final HashtagService hashtags;

    SearchController(PostService posts, ProfileService profiles, HashtagService hashtags) {
        this.posts = posts;
        this.profiles = profiles;
        this.hashtags = hashtags;
    }

    // ---- 検索(F-SEARCH-01〜05) ----

    @GetMapping("/api/search/posts")
    @Operation(summary = "投稿の検索",
            description = "投稿の本文(返信を含む)を、部分一致で探す。空白(全角・半角)で区切ると、すべての言葉を含むものを探す(最初の 10 語まで)。"
                    + "日本語の文章も、そのまま文字の並びとして探せる。`%` と `_` は、ふつうの文字として探す。"
                    + "新しい順に返す。続きは、前の結果の `nextCursor` を `before` に渡して取る。"
                    + "削除・非表示の投稿、利用停止中のユーザーと、ブロック・ミュートの関係にある人の投稿は出ない")
    @ApiResponse(responseCode = "200", description = "投稿の一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "400", description = "検索する言葉が空、または 100 文字を超える")
    PostService.Page searchPosts(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = Q, example = "カフェ 勉強") @RequestParam(defaultValue = "") String q,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "1234") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        List<String> words = Arrays.stream(validQuery(q).split("[\s　]+")).distinct().limit(MAX_WORDS).toList();
        return posts.search(words, me.id(), before, limit);
    }

    @GetMapping("/api/search/users")
    @Operation(summary = "ユーザーの検索",
            description = "ユーザーID・表示名を、部分一致で探す。ユーザーIDが言葉で始まるものを先に、次にフォロワーの多い順に、最大 20 件返す。"
                    + "利用停止中のユーザーと、ブロックの関係にあるユーザーは出ない。ミュート中のユーザーは出る(ミュートを解除できるように)")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧")
    @ApiResponse(responseCode = "400", description = "検索する言葉が空、または 100 文字を超える")
    UserResults searchUsers(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = Q, example = "hana") @RequestParam(defaultValue = "") String q) {
        return new UserResults(profiles.search(validQuery(q), me.id()));
    }

    @GetMapping("/api/search/tags")
    @Operation(summary = "ハッシュタグの検索",
            description = "ハッシュタグの名前を、部分一致で探す(先頭の `#` は無視する)。名前が言葉で始まるものを先に、次に投稿の多い順に、最大 20 件返す")
    @ApiResponse(responseCode = "200", description = "ハッシュタグの一覧")
    @ApiResponse(responseCode = "400", description = "検索する言葉が空、または 100 文字を超える")
    TagResults searchTags(
            @Parameter(description = Q, example = "spring") @RequestParam(defaultValue = "") String q) {
        return new TagResults(hashtags.search(validQuery(q), 20));
    }

    // ---- トレンド(F-TREND-01〜03) ----

    /** 直近 24 時間のハッシュタグ(上位 10 件)と人気投稿(上位 10 件)。呼ばれるたびに集計する */
    @GetMapping("/api/trends")
    @Operation(summary = "トレンド",
            description = "直近 24 時間の、よく使われたハッシュタグ(使われた投稿の数が多い順。返信も数える)と、人気投稿(いいねの数が多い順)を、それぞれ上位 10 件返す。"
                    + "呼ばれるたびに集計する。削除・非表示の投稿と利用停止中のユーザーの投稿は数えない。人気投稿は、見る人のブロック・ミュートでも絞る")
    @ApiResponse(responseCode = "200", description = "ハッシュタグと人気投稿")
    Trends trends(@AuthenticationPrincipal LoginUser me) {
        return new Trends(hashtags.trending(10), posts.popular(me.id(), 10));
    }

    @GetMapping("/api/tags/{name}/posts")
    @Operation(summary = "ハッシュタグの投稿の一覧",
            description = "そのハッシュタグを含む投稿(返信を含む)を、新しい順に返す。続きは、前の結果の `nextCursor` を `before` に渡して取る。"
                    + "ハッシュタグは、大文字・小文字、全角・半角を区別しない")
    @ApiResponse(responseCode = "200", description = "投稿の一覧。`nextCursor` が `null` なら、それ以上はない(そのタグの投稿がなくても、空の一覧を返す)")
    PostService.Page tagPosts(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "ハッシュタグの名前(`#` は付けない)", example = "springboot") @PathVariable String name,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "1234") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return posts.byTag(name, me.id(), before, limit);
    }

    /** 前後の空白を取り除いて 1〜100 文字(見た目の 1 文字を 1 文字として数える) */
    private static String validQuery(String q) {
        String query = q.strip();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EMPTY_QUERY);
        }
        if (query.codePointCount(0, query.length()) > MAX_QUERY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, QUERY_TOO_LONG);
        }
        return query;
    }
}
