package com.example.sns.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** ブロック・ミュート(機能定義書 3.11、1.6 の V-04・V-05、F-NOTIF-06)。 */
class BlockMuteApiTest extends ApiTestSupport {

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

    // ---- ミュート ----

    @Test
    void ミュートすると自分の一覧からだけ消え_相手からは見え_通知も届かない() throws Exception {
        long hanakoPost = post(hanako, "はなこの投稿 #topic");
        taro.put("/api/users/hanako/follow", "");
        taro.put("/api/users/hanako/mute", "").andExpect(status().isNoContent());
        taro.put("/api/users/hanako/mute", "").andExpect(status().isNoContent()); // 何度送っても同じ

        taro.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/timeline?tab=following").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/tags/topic/posts").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/search/posts?q=hanako%E3%81%AE").andExpect(jsonPath("$.items.length()").value(0));
        // 自分で開いた投稿の詳細は表示する。ユーザーの検索ではミュート中の人も出す(解除できるように)
        taro.get("/api/posts/" + hanakoPost).andExpect(jsonPath("$.unavailable").value(false));
        taro.get("/api/search/users?q=hanako").andExpect(jsonPath("$.items.length()").value(1));
        taro.get("/api/users/hanako").andExpect(jsonPath("$.mutedByMe").value(true));
        // プロフィールを開けば、ミュート中の人の投稿も見られる
        taro.get("/api/users/hanako/posts").andExpect(jsonPath("$.items.length()").value(1));

        // はなこからは、たろうの投稿は見える(ミュートされていることは分からない)
        post(taro, "たろうの投稿");
        hanako.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(2));
        hanako.get("/api/users/taro").andExpect(jsonPath("$.mutedByMe").value(false));

        // ミュートした人からの通知は届かない
        long taroPost = post(taro, "いいねされる投稿");
        hanako.put("/api/posts/" + taroPost + "/like", "").andExpect(status().isOk());
        taro.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(0));

        taro.delete("/api/users/hanako/mute").andExpect(status().isNoContent());
        taro.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(3));
        taro.get("/api/me/mutes").andExpect(jsonPath("$.items.length()").value(0));
    }

    // ---- ブロック ----

    @Test
    void ブロックするとお互いの投稿が見えず_フォローも解除される() throws Exception {
        long hanakoPost = post(hanako, "はなこの投稿 #topic");
        long taroPost = post(taro, "たろうの投稿 #topic");
        taro.put("/api/users/hanako/follow", "");
        hanako.put("/api/users/taro/follow", "");
        taro.put("/api/posts/" + hanakoPost + "/bookmark", "");

        taro.put("/api/users/hanako/block", "").andExpect(status().isNoContent());

        for (Browser browser : List.of(taro, hanako)) {
            browser.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(1));
            browser.get("/api/tags/topic/posts").andExpect(jsonPath("$.items.length()").value(1));
        }
        taro.get("/api/posts/" + hanakoPost).andExpect(status().isNotFound());
        hanako.get("/api/posts/" + taroPost).andExpect(status().isNotFound());
        taro.get("/api/bookmarks").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/search/users?q=hanako").andExpect(jsonPath("$.items.length()").value(0));
        // お互いのフォローは解除されている
        taro.get("/api/users/taro").andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.followerCount").value(0));

        // ブロックした側はプロフィールを見られる(投稿は出ない)。された側には「見つからない」
        taro.get("/api/users/hanako").andExpect(status().isOk()).andExpect(jsonPath("$.blockedByMe").value(true));
        taro.get("/api/users/hanako/posts").andExpect(jsonPath("$.items.length()").value(0));
        hanako.get("/api/users/taro").andExpect(status().isNotFound());
        taro.get("/api/me/blocks").andExpect(jsonPath("$.items[0].handle").value("hanako"));

        taro.delete("/api/users/hanako/block").andExpect(status().isNoContent());
        taro.get("/api/posts/" + hanakoPost).andExpect(status().isOk());
        taro.get("/api/bookmarks").andExpect(jsonPath("$.items.length()").value(1)); // ブックマーク自体は残っている
    }

    @Test
    void ブロックの関係にある人には_フォロー_返信_いいね_ブックマークができない() throws Exception {
        long hanakoPost = post(hanako, "はなこの投稿");
        hanako.put("/api/users/taro/block", "");

        taro.put("/api/users/hanako/follow", "").andExpect(status().isNotFound()); // ブロックされた側には相手が見えない
        hanako.put("/api/users/taro/follow", "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ProfileService.CANNOT_FOLLOW_BLOCKED));
        taro.put("/api/posts/" + hanakoPost + "/like", "").andExpect(status().isForbidden());
        taro.put("/api/posts/" + hanakoPost + "/bookmark", "").andExpect(status().isNotFound());
        taro.multipart("/api/posts", List.of(), "text", "返信", "replyToId", Long.toString(hanakoPost))
                .andExpect(status().isForbidden());
    }

    @Test
    void ブロックの関係にある人の返信は一覧に出ず_通知も届かない() throws Exception {
        long taroPost = post(taro, "たろうの投稿");
        createUser("ken", Role.USER);
        Browser ken = loggedInAs("ken");
        post(hanako, "はなこの返信 @taro", "replyToId", Long.toString(taroPost));
        post(ken, "けんの返信", "replyToId", Long.toString(taroPost));
        taro.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(2));

        taro.put("/api/users/hanako/block", "");
        taro.get("/api/posts/" + taroPost + "/replies")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("けんの返信"));
        // ブロックした人からの通知は、すでにあるものも出さない
        taro.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(1));
        taro.put("/api/users/hanako/follow", "").andExpect(status().isForbidden());
    }

    @Test
    void ブロック_ミュートしている人の新しい投稿は_リアルタイムでも届かない() throws Exception {
        createUser("ken", Role.USER);
        Browser ken = loggedInAs("ken");
        MockHttpServletResponse taroStream = taro.get("/api/stream").andExpect(request().asyncStarted())
                .andReturn().getResponse();
        taro.put("/api/users/hanako/block", "");
        taro.put("/api/users/ken/mute", "");
        post(hanako, "はなこの新しい投稿");
        post(ken, "けんの新しい投稿");
        assertThat(taroStream.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("event:post-created");
    }

    @Test
    void 自分自身はブロック_ミュートできない() throws Exception {
        taro.put("/api/users/taro/block", "").andExpect(status().isBadRequest());
        taro.put("/api/users/taro/mute", "").andExpect(status().isBadRequest());
        taro.put("/api/users/nobody/block", "").andExpect(status().isNotFound());
    }

    @Test
    void お互いにブロックしていても_自分のブロックは解除できる() throws Exception {
        taro.put("/api/users/hanako/block", "");
        hanako.put("/api/users/taro/block", "");
        taro.delete("/api/users/hanako/block").andExpect(status().isNoContent());
        taro.get("/api/me/blocks").andExpect(jsonPath("$.items.length()").value(0));
        // はなこからはまだブロックされているので、プロフィールは見えない
        taro.get("/api/users/hanako").andExpect(status().isNotFound());
    }
}
