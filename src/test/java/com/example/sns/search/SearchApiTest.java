package com.example.sns.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ハッシュタグ・トレンド・検索(機能定義書 3.9・3.10)。 */
class SearchApiTest extends ApiTestSupport {

    private Browser taro;
    private Browser hanako;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        createUser("hanako", Role.USER);
        taro = loggedInAs("taro");
        hanako = loggedInAs("hanako");
    }

    private long post(Browser browser, String text, String... extra) throws Exception {
        String[] params = new String[2 + extra.length];
        params[0] = "text";
        params[1] = text;
        System.arraycopy(extra, 0, params, 2, extra.length);
        String body = browser.multipart("/api/posts", List.of(), params)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** 画面(encodeURIComponent)と同じく、空白は %20 にする(URLEncoder は + にするため置き換える) */
    private static String q(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---- ハッシュタグ ----

    @Test
    void ハッシュタグの投稿一覧_返信も含み_編集と削除で付け直す() throws Exception {
        long first = post(taro, "はじめての #SpringBoot");
        post(hanako, "返信も #springboot", "replyToId", Long.toString(first));
        long edited = post(taro, "あとで書き換える #Docker");

        taro.get("/api/tags/springboot/posts").andExpect(jsonPath("$.items.length()").value(2));
        taro.get("/api/tags/" + q("#ＳｐｒｉｎｇＢｏｏｔ") + "/posts").andExpect(jsonPath("$.items.length()").value(2));

        taro.put("/api/posts/" + edited, "{\"text\":\"書き換えた #SpringBoot\"}");
        taro.get("/api/tags/docker/posts").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/tags/springboot/posts").andExpect(jsonPath("$.items.length()").value(3));

        taro.delete("/api/posts/" + first);
        taro.get("/api/tags/springboot/posts")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("書き換えた #SpringBoot"));
    }

    // ---- トレンド ----

    @Test
    void トレンドのタグは直近24時間の投稿の数の多い順で_古い投稿と非表示は数えない() throws Exception {
        post(taro, "#カフェ巡り 1軒目");
        post(hanako, "#カフェ巡り 2軒目 #写真");
        long hidden = post(hanako, "#カフェ巡り 3軒目(非表示にされる)");
        long old = post(taro, "#写真 昔の写真 #docker");
        post(taro, "#写真 今日の写真");
        post(taro, "#写真 もう一枚");
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        jdbc.update("update post_hashtags set created_at = now() - interval '25 hours' where post_id = ?", old);

        taro.get("/api/trends")
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].name").value("写真"))
                .andExpect(jsonPath("$.tags[0].postCount").value(3))
                .andExpect(jsonPath("$.tags[1].name").value("カフェ巡り"))
                .andExpect(jsonPath("$.tags[1].postCount").value(2));
    }

    @Test
    void 人気投稿はいいねが1件以上の返信以外を_いいね数の多い順に() throws Exception {
        long one = post(hanako, "いいね 1 件");
        long two = post(hanako, "いいね 2 件");
        post(hanako, "いいね 0 件");
        long reply = post(hanako, "返信にはいいねしても出ない", "replyToId", Long.toString(one));
        long old = post(hanako, "昔の投稿");
        createUser("ken", Role.USER);
        Browser ken = loggedInAs("ken");
        taro.put("/api/posts/" + one + "/like", "");
        taro.put("/api/posts/" + two + "/like", "");
        ken.put("/api/posts/" + two + "/like", "");
        taro.put("/api/posts/" + reply + "/like", "");
        taro.put("/api/posts/" + old + "/like", "");
        jdbc.update("update posts set created_at = now() - interval '25 hours' where id = ?", old);

        taro.get("/api/trends")
                .andExpect(jsonPath("$.popularPosts.length()").value(2))
                .andExpect(jsonPath("$.popularPosts[0].text").value("いいね 2 件"))
                .andExpect(jsonPath("$.popularPosts[0].likeCount").value(2))
                .andExpect(jsonPath("$.popularPosts[0].likedByMe").value(true))
                .andExpect(jsonPath("$.popularPosts[1].text").value("いいね 1 件"));
    }

    // ---- 検索 ----

    @Test
    void 投稿の検索_日本語の文章_すべての言葉を含む_全角半角を区別しない() throws Exception {
        post(taro, "今日は渋谷のカフェでSpring Bootの勉強をしました");
        post(hanako, "新宿のカフェでケーキを食べました");
        post(hanako, "ＳＰＲＩＮＧの設定ファイルを書いた");

        taro.get("/api/search/posts?q=" + q("カフェ")).andExpect(jsonPath("$.items.length()").value(2));
        taro.get("/api/search/posts?q=" + q("カフェ　勉強")) // 全角の空白で区切る
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].author.handle").value("taro"));
        taro.get("/api/search/posts?q=" + q("spring")).andExpect(jsonPath("$.items.length()").value(2));
        taro.get("/api/search/posts?q=" + q("ｓｐｒｉｎｇ ｂｏｏｔ")).andExpect(jsonPath("$.items.length()").value(1));
        taro.get("/api/search/posts?q=" + q("ラーメン")).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 検索の言葉の_パーセントやアンダースコアはただの文字として扱う() throws Exception {
        post(taro, "割引は 50% です");
        post(taro, "割引は 500 円です");
        taro.get("/api/search/posts?q=" + q("50%")).andExpect(jsonPath("$.items.length()").value(1));
        taro.get("/api/search/posts?q=" + q("_")).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 検索の言葉は1から100文字() throws Exception {
        taro.get("/api/search/posts?q=" + q("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SearchController.EMPTY_QUERY));
        taro.get("/api/search/posts?q=" + q("あ".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SearchController.QUERY_TOO_LONG));
        taro.get("/api/search/users?q=").andExpect(status().isBadRequest());
    }

    @Test
    void 検索結果から_削除_非表示_利用停止中の人の投稿は除く() throws Exception {
        long deleted = post(taro, "カフェ 削除する");
        long hidden = post(taro, "カフェ 非表示にされる");
        post(hanako, "カフェ 利用停止にされる人の投稿");
        post(taro, "カフェ 残る");
        taro.delete("/api/posts/" + deleted);
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        jdbc.update("update users set suspended = true where handle = 'hanako'");
        taro.get("/api/search/posts?q=" + q("カフェ"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("カフェ 残る"));
        taro.get("/api/search/users?q=hanako").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void ユーザーの検索_ユーザーIDで始まる人が先_次にフォロワーの多い順() throws Exception {
        createUser("cafe_lover", Role.USER);
        createUser("my_cafe", Role.USER);
        createUser("cafe_master", Role.USER);
        taro.put("/api/users/cafe_master/follow", "");
        hanako.put("/api/users/my_cafe/follow", "");
        hanako.put("/api/users/cafe_master/follow", "");

        taro.get("/api/search/users?q=" + q("@CAFE"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].handle").value("cafe_master"))
                .andExpect(jsonPath("$.items[0].followedByMe").value(true))
                .andExpect(jsonPath("$.items[1].handle").value("cafe_lover"))
                .andExpect(jsonPath("$.items[2].handle").value("my_cafe"));
        // 表示名でも探せる(createUser の表示名は「ユーザーID + さん」)
        taro.get("/api/search/users?q=" + q("hanakoさん")).andExpect(jsonPath("$.items[0].handle").value("hanako"));
    }

    @Test
    void ハッシュタグの検索_名前で始まるタグが先_次に投稿の数の多い順() throws Exception {
        post(taro, "#カフェ巡り #カフェ");
        post(taro, "#カフェ巡り");
        post(taro, "#おすすめカフェ #おすすめカフェ");
        taro.get("/api/search/tags?q=" + q("#カフェ"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("カフェ巡り"))
                .andExpect(jsonPath("$.items[0].postCount").value(2))
                .andExpect(jsonPath("$.items[1].name").value("カフェ"))
                .andExpect(jsonPath("$.items[2].name").value("おすすめカフェ"));
        assertThat(jdbc.queryForObject("select count(*) from post_hashtags", Long.class)).isEqualTo(4);
    }
}
