package com.example.sns.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** プロフィール・フォロー・「フォロー中」のタイムライン(機能定義書 3.2・3.5・3.6)。 */
class ProfileApiTest extends ApiTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    void フォローとフォロー解除_何度送っても同じ結果で_数が変わる() throws Exception {
        taro.put("/api/users/hanako/follow", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        taro.put("/api/users/HANAKO/follow", "").andExpect(jsonPath("$.followerCount").value(1));

        taro.get("/api/users/hanako")
                .andExpect(jsonPath("$.handle").value("hanako"))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.followsMe").value(false))
                .andExpect(jsonPath("$.me").value(false));
        // はなこから見た、たろうのプロフィール(たろうは 1 人をフォローし、はなこをフォローしている)
        hanako.get("/api/users/taro")
                .andExpect(jsonPath("$.followingCount").value(1))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followsMe").value(true))
                .andExpect(jsonPath("$.followedByMe").value(false));
        taro.get("/api/users/taro")
                .andExpect(jsonPath("$.me").value(true))
                .andExpect(jsonPath("$.followingCount").value(1));

        taro.delete("/api/users/hanako/follow")
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
        taro.delete("/api/users/hanako/follow").andExpect(status().isOk());
    }

    @Test
    void 自分自身はフォローできず_存在しない人と利用停止中の人は404() throws Exception {
        taro.put("/api/users/taro/follow", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ProfileService.CANNOT_FOLLOW_SELF));
        taro.put("/api/users/nobody/follow", "").andExpect(status().isNotFound());
        taro.get("/api/users/nobody").andExpect(status().isNotFound());

        User ken = users.findByHandleLower("ken").orElseThrow();
        jdbc.update("update users set suspended = true where id = ?", ken.getId());
        taro.get("/api/users/ken").andExpect(status().isNotFound());
        taro.put("/api/users/ken/follow", "").andExpect(status().isNotFound());
    }

    @Test
    void フォロー中のタイムラインには_自分とフォローしている人の投稿だけが出る() throws Exception {
        post(taro, "たろうの投稿");
        post(hanako, "はなこの投稿");
        post(loggedInAs("ken"), "けんの投稿");

        taro.get("/api/timeline?tab=following")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("たろうの投稿"));
        taro.put("/api/users/hanako/follow", "");
        taro.get("/api/timeline?tab=following")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("はなこの投稿"))
                .andExpect(jsonPath("$.items[1].text").value("たろうの投稿"));
        taro.get("/api/timeline?tab=all").andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void プロフィールの投稿一覧_投稿_返信を含む_画像のタブ() throws Exception {
        long first = post(hanako, "はなこの投稿");
        post(hanako, "はなこの返信", "replyToId", Long.toString(first));
        hanako.multipart("/api/posts", List.of(com.example.sns.post.TestImages.image("png", 2, 2)), "text", "画像付き");

        taro.get("/api/users/hanako")
                .andExpect(jsonPath("$.postCount").value(3));
        taro.get("/api/users/hanako/posts")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("画像付き"));
        taro.get("/api/users/hanako/posts?tab=replies").andExpect(jsonPath("$.items.length()").value(3));
        taro.get("/api/users/hanako/posts?tab=media")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("画像付き"));
        taro.get("/api/users/hanako/posts?tab=wrong").andExpect(status().isBadRequest());
    }

    @Test
    void 管理者に非表示にされた投稿は_本人のプロフィールにだけ印つきで出る() throws Exception {
        long hidden = post(hanako, "非表示にされた投稿");
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        hanako.get("/api/users/hanako/posts")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].hidden").value(true));
        taro.get("/api/users/hanako/posts").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void フォロー中とフォロワーの一覧_関係の表示と_SQLの回数() throws Exception {
        taro.put("/api/users/hanako/follow", "");
        taro.put("/api/users/ken/follow", "");
        hanako.put("/api/users/taro/follow", "");
        for (int i = 0; i < 22; i++) {
            createUser("fan" + i, Role.USER);
            loggedInAs("fan" + i).put("/api/users/taro/follow", "");
        }

        taro.get("/api/users/taro/following")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].handle").value("ken"))
                .andExpect(jsonPath("$.items[1].handle").value("hanako"))
                .andExpect(jsonPath("$.items[1].followedByMe").value(true))
                .andExpect(jsonPath("$.items[1].followsMe").value(true));

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        String first = taro.get("/api/users/taro/followers")
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].handle").value("fan21"))
                .andReturn().getResponse().getContentAsString();
        // ログイン中のユーザーの確認 1 + 一覧の人 1 + フォロワー(相手つき)1 + フォローしているか 1 + されているか 1
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);

        long cursor = ((Number) JsonPath.read(first, "$.nextCursor")).longValue();
        taro.get("/api/users/taro/followers?before=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].handle").value("hanako"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }
}
