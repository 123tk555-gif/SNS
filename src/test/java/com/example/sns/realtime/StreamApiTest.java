package com.example.sns.realtime;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * リアルタイム配信(機能定義書 3.8)と新着の取り込み(3.5)。
 * /api/stream につないだままにして、別の人の操作で、つないでいる側の応答にイベントが書き込まれるかを確かめる。
 */
class StreamApiTest extends ApiTestSupport {

    @Autowired
    private StreamRegistry registry;

    private Browser taro;
    private Browser hanako;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        createUser("hanako", Role.USER);
        taro = loggedInAs("taro");
        hanako = loggedInAs("hanako");
    }

    private MockHttpServletResponse connect(Browser browser) throws Exception {
        return browser.get("/api/stream")
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
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

    private static String events(MockHttpServletResponse stream) throws Exception {
        return stream.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 新しい投稿が_接続中の他の人に届き_投稿者本人には届かない() throws Exception {
        MockHttpServletResponse hanakoStream = connect(hanako);
        MockHttpServletResponse taroStream = connect(taro);

        post(taro, "リアルタイムの投稿");
        assertThat(events(hanakoStream))
                .contains("event:post-created")
                .contains("リアルタイムの投稿")
                .contains("\"followedByMe\":false");
        assertThat(events(taroStream)).doesNotContain("event:post-created");

        // フォローしていれば followedByMe が true になる(画面は「フォロー中」にも足す)
        hanako.put("/api/users/taro/follow", "");
        post(taro, "フォローされてからの投稿");
        assertThat(events(hanakoStream)).contains("\"followedByMe\":true");
    }

    @Test
    void 返信は配信しない_削除は配信する() throws Exception {
        long postId = post(taro, "元の投稿");
        MockHttpServletResponse hanakoStream = connect(hanako);
        post(taro, "返信", "replyToId", Long.toString(postId));
        assertThat(events(hanakoStream)).doesNotContain("event:post-created");

        taro.delete("/api/posts/" + postId);
        assertThat(events(hanakoStream)).contains("event:post-deleted").contains("\"id\":" + postId);
    }

    @Test
    void 通知が届くと_受け取る人に未読の数と一緒に届く() throws Exception {
        long postId = post(hanako, "はなこの投稿");
        MockHttpServletResponse hanakoStream = connect(hanako);
        MockHttpServletResponse taroStream = connect(taro);

        taro.put("/api/posts/" + postId + "/like", "");
        assertThat(events(hanakoStream))
                .contains("event:notification")
                .contains("\"unreadCount\":1")
                .contains("\"type\":\"LIKE\"");
        assertThat(events(taroStream)).doesNotContain("event:notification");
    }

    @Test
    void ログインしていないとつなげない_接続は1人5つまで() throws Exception {
        browser().get("/api/stream").andExpect(status().isUnauthorized());
        long hanakoId = users.findByHandleLower("hanako").orElseThrow().getId();
        for (int i = 0; i < 7; i++) {
            connect(hanako);
        }
        assertThat(registry.connections(hanakoId)).isEqualTo(StreamRegistry.MAX_PER_USER);
    }

    @Test
    void 利用停止にすると接続が切れる() throws Exception {
        createUser("admin_user", Role.ADMIN);
        long hanakoId = users.findByHandleLower("hanako").orElseThrow().getId();
        connect(hanako);
        assertThat(registry.connections(hanakoId)).isEqualTo(1);
        loggedInAs("admin_user").put("/api/admin/users/" + hanakoId + "/suspension", "{\"suspended\":true}")
                .andExpect(status().isOk());
        assertThat(registry.connections(hanakoId)).isZero();
    }

    @Test
    void 新着の取り込み_afterより新しい投稿を古い順に返す() throws Exception {
        long first = post(taro, "1つ目");
        post(hanako, "2つ目");
        post(taro, "3つ目");
        taro.get("/api/timeline?after=" + first)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("2つ目"))
                .andExpect(jsonPath("$.items[1].text").value("3つ目"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        // 「フォロー中」では、フォローしていないはなこの投稿は出ない
        taro.get("/api/timeline?tab=following&after=" + first)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("3つ目"));
    }
}
