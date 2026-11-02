package com.example.sns.notification;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 通知(機能定義書 3.7)。 */
class NotificationApiTest extends ApiTestSupport {

    private Browser taro;
    private Browser hanako;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        createUser("hanako", Role.USER);
        createUser("ken", Role.USER);
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

    @Test
    void いいねされると通知が届き_付け直しても増えない_自分のいいねでは届かない() throws Exception {
        long postId = post(hanako, "はなこの投稿です。この文は四十文字を超えるので、通知では途中までしか表示されません");
        taro.put("/api/posts/" + postId + "/like", "");
        hanako.put("/api/posts/" + postId + "/like", ""); // 自分の投稿に自分でいいね

        hanako.get("/api/notifications")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("LIKE"))
                .andExpect(jsonPath("$.items[0].actor.handle").value("taro"))
                .andExpect(jsonPath("$.items[0].post.id").value(postId))
                .andExpect(jsonPath("$.items[0].post.textPreview")
                        .value("はなこの投稿です。この文は四十文字を超えるので、通知では途中までしか表示されませ…"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        hanako.get("/api/notifications/unread-count").andExpect(jsonPath("$.unreadCount").value(1));
        hanako.get("/api/auth/me").andExpect(jsonPath("$.unreadNotificationCount").value(1));

        taro.delete("/api/posts/" + postId + "/like");
        taro.put("/api/posts/" + postId + "/like", "");
        taro.put("/api/posts/" + postId + "/like", ""); // いいね済みでもう一度
        hanako.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(1));
        taro.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 返信_フォロー_メンションの通知() throws Exception {
        long postId = post(hanako, "はなこの投稿");
        // はなこへの返信で、はなこにもメンションしている → はなこには「返信」だけ。けんには「メンション」
        post(taro, "返信です @hanako @ken @no_such_user", "replyToId", Long.toString(postId));
        taro.put("/api/users/hanako/follow", "");
        taro.put("/api/users/hanako/follow", "");

        hanako.get("/api/notifications")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("FOLLOW"))
                .andExpect(jsonPath("$.items[0].post").isEmpty())
                .andExpect(jsonPath("$.items[1].type").value("REPLY"));
        loggedInAs("ken").get("/api/notifications")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MENTION"));
    }

    @Test
    void メンションの通知は最初の10人まで_編集で増えた相手にだけ届く() throws Exception {
        StringBuilder text = new StringBuilder("みなさんへ");
        for (int i = 0; i < 11; i++) {
            createUser("user" + i, Role.USER);
            text.append(" @user").append(i);
        }
        post(taro, text.toString());
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from notifications where type = 'MENTION'", Long.class))
                .isEqualTo(10);

        long postId = post(taro, "はじめまして");
        taro.put("/api/posts/" + postId, "{\"text\":\"はじめまして @hanako\"}").andExpect(status().isOk());
        taro.put("/api/posts/" + postId, "{\"text\":\"はじめまして @hanako @ken\"}").andExpect(status().isOk());
        hanako.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 既読にすると未読の数が減る() throws Exception {
        long postId = post(hanako, "はなこの投稿");
        taro.put("/api/posts/" + postId + "/like", "");
        taro.put("/api/users/hanako/follow", "");
        String body = hanako.get("/api/notifications").andReturn().getResponse().getContentAsString();
        long newest = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();
        long older = ((Number) JsonPath.read(body, "$.items[1].id")).longValue();

        hanako.post("/api/notifications/read", "{\"upToId\":" + older + "}")
                .andExpect(jsonPath("$.unreadCount").value(1));
        hanako.post("/api/notifications/read", "{\"upToId\":" + newest + "}")
                .andExpect(jsonPath("$.unreadCount").value(0));
        hanako.get("/api/notifications").andExpect(jsonPath("$.items[0].read").value(true));
        hanako.post("/api/notifications/read", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void 対象の投稿が削除_非表示になった通知と_操作した人が利用停止中の通知は出さない() throws Exception {
        long deleted = post(hanako, "削除する投稿");
        long hidden = post(hanako, "非表示にされる投稿");
        taro.put("/api/posts/" + deleted + "/like", "");
        taro.put("/api/posts/" + hidden + "/like", "");
        Browser ken = loggedInAs("ken");
        ken.put("/api/users/hanako/follow", "");
        hanako.get("/api/notifications/unread-count").andExpect(jsonPath("$.unreadCount").value(3));

        hanako.delete("/api/posts/" + deleted).andExpect(status().isNoContent());
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        jdbc.update("update users set suspended = true where handle = 'ken'");

        hanako.get("/api/notifications").andExpect(jsonPath("$.items.length()").value(0));
        hanako.get("/api/notifications/unread-count").andExpect(jsonPath("$.unreadCount").value(0));
    }
}
