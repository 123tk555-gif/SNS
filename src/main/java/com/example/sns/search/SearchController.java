package com.example.sns.search;

import com.example.sns.hashtag.HashtagService;
import com.example.sns.post.PostResponse;
import com.example.sns.post.PostService;
import com.example.sns.profile.ProfileService;
import com.example.sns.profile.UserSummary;
import com.example.sns.security.LoginUser;
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
class SearchController {

    static final int MAX_QUERY = 100;
    /** 一度に探せる言葉の数(多すぎる言葉で、データベースに重い問い合わせをさせないため) */
    static final int MAX_WORDS = 10;
    static final String EMPTY_QUERY = "検索する言葉を入力してください";
    static final String QUERY_TOO_LONG = "100文字以内で入力してください";

    record Trends(List<HashtagService.TagCount> tags, List<PostResponse> popularPosts) {
    }

    record UserResults(List<UserSummary> items) {
    }

    record TagResults(List<HashtagService.TagCount> items) {
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
    PostService.Page searchPosts(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        List<String> words = Arrays.stream(validQuery(q).split("[\\s　]+")).distinct().limit(MAX_WORDS).toList();
        return posts.search(words, me.id(), before, limit);
    }

    @GetMapping("/api/search/users")
    UserResults searchUsers(@AuthenticationPrincipal LoginUser me, @RequestParam(defaultValue = "") String q) {
        return new UserResults(profiles.search(validQuery(q), me.id()));
    }

    @GetMapping("/api/search/tags")
    TagResults searchTags(@RequestParam(defaultValue = "") String q) {
        return new TagResults(hashtags.search(validQuery(q), 20));
    }

    // ---- トレンド(F-TREND-01〜03) ----

    /** 直近 24 時間のハッシュタグ(上位 10 件)と人気投稿(上位 10 件)。呼ばれるたびに集計する */
    @GetMapping("/api/trends")
    Trends trends(@AuthenticationPrincipal LoginUser me) {
        return new Trends(hashtags.trending(10), posts.popular(me.id(), 10));
    }

    @GetMapping("/api/tags/{name}/posts")
    PostService.Page tagPosts(@AuthenticationPrincipal LoginUser me, @PathVariable String name,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
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
