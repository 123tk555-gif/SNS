package com.example.sns.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** いいね・ブックマーク(機能定義書 3.4)。 */
class ReactionApiTest extends ApiTestSupport {

    private Browser taro;
    private Browser hanako;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        createUser("hanako", Role.USER);
        taro = loggedInAs("taro");
        hanako = loggedInAs("hanako");
        postId = post(hanako, "はなこの投稿");
    }

    private long post(Browser browser, String text) throws Exception {
        String body = browser.multipart("/api/posts", List.of(), "text", text)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void いいねと取り消し_何度送っても同じ結果() throws Exception {
        taro.put("/api/posts/" + postId + "/like", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        taro.put("/api/posts/" + postId + "/like", "")
                .andExpect(jsonPath("$.likeCount").value(1));
        hanako.put("/api/posts/" + postId + "/like", "")
                .andExpect(jsonPath("$.likeCount").value(2));

        // 投稿の JSON にも、いいね数と「自分がいいね済みか」が入る
        taro.get("/api/posts/" + postId)
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true));

        taro.delete("/api/posts/" + postId + "/like")
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
        taro.delete("/api/posts/" + postId + "/like").andExpect(jsonPath("$.likeCount").value(1));
        taro.get("/api/timeline")
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].likedByMe").value(false));
    }

    @Test
    void 削除_非表示の投稿と存在しない投稿にはいいねできない() throws Exception {
        long hidden = post(hanako, "非表示にされる投稿");
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        taro.put("/api/posts/" + hidden + "/like", "").andExpect(status().isNotFound());
        taro.put("/api/posts/999999/like", "").andExpect(status().isNotFound());
        hanako.delete("/api/posts/" + postId).andExpect(status().isNoContent());
        taro.put("/api/posts/" + postId + "/like", "").andExpect(status().isNotFound());
        taro.put("/api/posts/" + postId + "/bookmark", "").andExpect(status().isNotFound());
    }

    @Test
    void ブックマークは本人にしか見えず_新しい順に並ぶ() throws Exception {
        long second = post(hanako, "2つ目の投稿");
        taro.put("/api/posts/" + postId + "/bookmark", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
        taro.put("/api/posts/" + second + "/bookmark", "");
        taro.put("/api/posts/" + postId + "/bookmark", ""); // 2 回目も同じ結果

        taro.get("/api/bookmarks")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[0].bookmarkedByMe").value(true))
                .andExpect(jsonPath("$.items[1].id").value(postId));
        // はなこのブックマークは空(たろうのブックマークは見えない)
        hanako.get("/api/bookmarks").andExpect(jsonPath("$.items.length()").value(0));
        hanako.get("/api/posts/" + postId).andExpect(jsonPath("$.bookmarkedByMe").value(false));

        taro.delete("/api/posts/" + second + "/bookmark").andExpect(jsonPath("$.bookmarked").value(false));
        taro.get("/api/bookmarks").andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void ブックマークの一覧は20件ずつで_見られなくなった投稿は出さない() throws Exception {
        for (int i = 0; i < 22; i++) {
            taro.put("/api/posts/" + post(hanako, "投稿" + i) + "/bookmark", "");
        }
        String first = taro.get("/api/bookmarks")
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].text").value("投稿21"))
                .andReturn().getResponse().getContentAsString();
        long cursor = ((Number) JsonPath.read(first, "$.nextCursor")).longValue();
        taro.get("/api/bookmarks?before=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        jdbc.update("update posts set hidden = true where text = '投稿21'");
        taro.get("/api/bookmarks").andExpect(jsonPath("$.items[0].text").value("投稿20"));
    }

    @Test
    void 投稿を削除すると_いいねとブックマークも消える() throws Exception {
        taro.put("/api/posts/" + postId + "/like", "");
        taro.put("/api/posts/" + postId + "/bookmark", "");
        hanako.delete("/api/posts/" + postId).andExpect(status().isNoContent());
        taro.get("/api/bookmarks").andExpect(jsonPath("$.items.length()").value(0));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from likes", Long.class))
                .isZero();
    }
}
