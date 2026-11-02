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

/** 返信と投稿詳細(機能定義書 3.3 の F-POST-07, 08)。 */
class ReplyApiTest extends ApiTestSupport {

    private Browser taro;
    private Browser hanako;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        createUser("hanako", Role.USER);
        taro = loggedInAs("taro");
        hanako = loggedInAs("hanako");
        postId = post(taro, "たろうの投稿", null);
    }

    private long post(Browser browser, String text, Long replyToId) throws Exception {
        String body = (replyToId == null
                ? browser.multipart("/api/posts", List.of(), "text", text)
                : browser.multipart("/api/posts", List.of(), "text", text, "replyToId", replyToId.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 返信すると返信先が付き_タイムラインには出ず_返信数が増える() throws Exception {
        hanako.multipart("/api/posts", List.of(), "text", "返信です", "replyToId", Long.toString(postId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyTo.id").value(postId))
                .andExpect(jsonPath("$.replyTo.authorHandle").value("taro"))
                .andExpect(jsonPath("$.replyTo.unavailable").value(false));

        taro.get("/api/timeline")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].replyCount").value(1));
        taro.get("/api/posts/" + postId).andExpect(jsonPath("$.replyCount").value(1));
    }

    @Test
    void 返信の一覧は古い順で_続きはafterで取る() throws Exception {
        for (int i = 1; i <= 23; i++) {
            post(i % 2 == 0 ? taro : hanako, "返信" + i, postId);
        }
        String first = taro.get("/api/posts/" + postId + "/replies")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].text").value("返信1"))
                .andExpect(jsonPath("$.items[19].text").value("返信20"))
                .andReturn().getResponse().getContentAsString();
        long cursor = ((Number) JsonPath.read(first, "$.nextCursor")).longValue();
        taro.get("/api/posts/" + postId + "/replies?after=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].text").value("返信23"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        taro.get("/api/posts/999999/replies").andExpect(status().isNotFound());
    }

    @Test
    void 返信先が削除されたら_返信は残り_返信先は表示できない扱いになる() throws Exception {
        long reply = post(hanako, "返信です", postId);
        taro.delete("/api/posts/" + postId).andExpect(status().isNoContent());

        taro.get("/api/posts/" + postId).andExpect(jsonPath("$.unavailable").value(true));
        taro.get("/api/posts/" + postId + "/replies")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].replyTo.unavailable").value(true))
                .andExpect(jsonPath("$.items[0].replyTo.authorHandle").isEmpty());
        hanako.get("/api/posts/" + reply).andExpect(jsonPath("$.replyTo.unavailable").value(true));
    }

    @Test
    void 削除_非表示の投稿と存在しない投稿には返信できない() throws Exception {
        long hidden = post(taro, "非表示にされる投稿", null);
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        hanako.multipart("/api/posts", List.of(), "text", "返信", "replyToId", Long.toString(hidden))
                .andExpect(status().isNotFound());
        hanako.multipart("/api/posts", List.of(), "text", "返信", "replyToId", "999999")
                .andExpect(status().isNotFound());
    }

    @Test
    void 削除_非表示の返信は一覧に出さない() throws Exception {
        long deleted = post(hanako, "消す返信", postId);
        long hidden = post(hanako, "非表示にされる返信", postId);
        post(hanako, "残る返信", postId);
        hanako.delete("/api/posts/" + deleted).andExpect(status().isNoContent());
        jdbc.update("update posts set hidden = true where id = ?", hidden);

        taro.get("/api/posts/" + postId + "/replies")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("残る返信"));
        taro.get("/api/posts/" + postId).andExpect(jsonPath("$.replyCount").value(1));
    }
}
