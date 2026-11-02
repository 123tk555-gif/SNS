package com.example.sns.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.media.ImageStorage;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** 投稿の作成・取得・編集・削除と、タイムライン(機能定義書 3.3・3.5)。 */
class PostApiTest extends ApiTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Browser taro;

    @BeforeEach
    void login() throws Exception {
        createUser("taro", Role.USER);
        taro = loggedInAs("taro");
    }

    private long post(Browser browser, String text) throws Exception {
        String body = browser.multipart("/api/posts", List.of(), "text", text)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---- 作成 ----

    @Test
    void 本文だけの投稿_前後の空白を取り除き_投稿者の情報が付く() throws Exception {
        taro.multipart("/api/posts", List.of(), "text", "  はじめての投稿 #テスト  ")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.text").value("はじめての投稿 #テスト"))
                .andExpect(jsonPath("$.author.handle").value("taro"))
                .andExpect(jsonPath("$.author.displayName").value("taroさん"))
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editedAt").isEmpty())
                .andExpect(jsonPath("$.unavailable").value(false));
    }

    @Test
    void 本文も画像もない投稿と_1000文字を超える投稿はできない() throws Exception {
        taro.multipart("/api/posts", List.of(), "text", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PostService.EMPTY_POST));
        taro.multipart("/api/posts", List.of(), "text", "あ".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PostService.TEXT_TOO_LONG));
        // 絵文字も 1 文字として数える(Java の文字数では 2000 だが、見た目は 1000 文字)
        taro.multipart("/api/posts", List.of(), "text", "😀".repeat(1000)).andExpect(status().isCreated());
    }

    @Test
    void 画像付きの投稿_形式と大きさを中身から読み_説明はカンマを含んでもそのまま() throws Exception {
        List<MockMultipartFile> files = List.of(
                TestImages.image("png", 40, 30), TestImages.image("jpg", 64, 48),
                TestImages.image("gif", 10, 20), TestImages.webp(1600, 1200));
        String body = taro.multipart("/api/posts", files,
                        "text", "",
                        "alts", "夕焼け、川, 橋",
                        "alts", "",
                        "alts", "猫",
                        "alts", "ノートパソコン")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value(""))
                .andExpect(jsonPath("$.images.length()").value(4))
                .andExpect(jsonPath("$.images[0].alt").value("夕焼け、川, 橋"))
                .andExpect(jsonPath("$.images[0].width").value(40))
                .andExpect(jsonPath("$.images[0].height").value(30))
                .andExpect(jsonPath("$.images[1].alt").value(""))
                .andExpect(jsonPath("$.images[1].width").value(64))
                .andExpect(jsonPath("$.images[2].height").value(20))
                .andExpect(jsonPath("$.images[3].width").value(1600))
                .andExpect(jsonPath("$.images[3].height").value(1200))
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(body, "$.images[0].url");
        assertThat(url).matches("/media/posts/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        taro.get(url).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        browser().get(url).andExpect(status().isUnauthorized());
        String webpUrl = JsonPath.read(body, "$.images[3].url");
        assertThat(webpUrl).endsWith(".webp");
    }

    @Test
    void 画像でないファイル_11枚以上_5MBを超える画像は受け付けない() throws Exception {
        taro.multipart("/api/posts", List.of(TestImages.fake()), "text", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ImageStorage.WRONG_FORMAT));

        List<MockMultipartFile> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(TestImages.image("png", 2, 2));
        }
        taro.multipart("/api/posts", eleven, "text", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PostService.TOO_MANY_IMAGES));

        byte[] big = new byte[(int) ImageStorage.MAX_BYTES + 1];
        System.arraycopy(TestImages.bytes("png", 2, 2), 0, big, 0, TestImages.bytes("png", 2, 2).length);
        taro.multipart("/api/posts", List.of(new MockMultipartFile("images", "big.png", "image/png", big)), "text", "x")
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value(ImageStorage.TOO_LARGE));
    }

    @Test
    void パスを使って保存場所の外のファイルは取れない() throws Exception {
        // 「..」を含む URL は Spring Security が受け付けない(400)。どちらにしてもファイルは返らない
        taro.get("/media/../pom.xml").andExpect(status().is4xxClientError());
        taro.get("/media/posts/%2e%2e/%2e%2e/pom.xml").andExpect(status().is4xxClientError());
        taro.get("/media/posts/no-such.png").andExpect(status().isNotFound());
    }

    // ---- 編集・削除 ----

    @Test
    void 自分の投稿は編集でき_編集済みになる() throws Exception {
        long id = post(taro, "書き間違い");
        taro.put("/api/posts/" + id, "{\"text\":\"  書き直しました  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("書き直しました"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
        taro.put("/api/posts/" + id, "{\"text\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PostService.EMPTY_POST));
    }

    @Test
    void 他人の投稿は_APIを直接呼んでも編集_削除できない() throws Exception {
        long id = post(taro, "たろうの投稿");
        createUser("hanako", Role.USER);
        Browser hanako = loggedInAs("hanako");
        hanako.get("/api/posts/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.mine").value(false));
        hanako.put("/api/posts/" + id, "{\"text\":\"乗っ取り\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PostService.NOT_MINE));
        hanako.delete("/api/posts/" + id).andExpect(status().isForbidden());
        taro.get("/api/posts/" + id).andExpect(jsonPath("$.text").value("たろうの投稿"));
    }

    @Test
    void 削除すると_表示できない投稿になり_画像のファイルも消える() throws Exception {
        String body = taro.multipart("/api/posts", List.of(TestImages.image("png", 4, 4)), "text", "消す投稿")
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        String url = JsonPath.read(body, "$.images[0].url");

        taro.delete("/api/posts/" + id).andExpect(status().isNoContent());
        taro.get("/api/posts/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailable").value(true))
                .andExpect(jsonPath("$.text").value(""));
        taro.get(url).andExpect(status().isNotFound());
        taro.delete("/api/posts/" + id).andExpect(status().isNotFound());
        taro.get("/api/posts/999999").andExpect(status().isNotFound());
    }

    // ---- タイムライン ----

    @Test
    void タイムラインは新しい順に20件ずつ_続きはカーソルで取る() throws Exception {
        for (int i = 1; i <= 25; i++) {
            post(taro, "投稿" + i);
        }
        String first = taro.get("/api/timeline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].text").value("投稿25"))
                .andExpect(jsonPath("$.items[19].text").value("投稿6"))
                .andReturn().getResponse().getContentAsString();
        long cursor = ((Number) JsonPath.read(first, "$.nextCursor")).longValue();

        taro.get("/api/timeline?before=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].text").value("投稿5"))
                .andExpect(jsonPath("$.items[4].text").value("投稿1"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        taro.get("/api/timeline?limit=51").andExpect(status().isBadRequest());
        taro.get("/api/timeline?tab=following").andExpect(status().isBadRequest());
    }

    @Test
    void タイムラインに出ない投稿_削除_非表示_利用停止中の投稿者_返信() throws Exception {
        long shown = post(taro, "表示される");
        long deleted = post(taro, "削除した");
        taro.delete("/api/posts/" + deleted).andExpect(status().isNoContent());
        long hidden = post(taro, "非表示にされた");
        jdbc.update("update posts set hidden = true where id = ?", hidden);
        long reply = post(taro, "返信");
        jdbc.update("update posts set reply_to_id = ? where id = ?", shown, reply);

        User suspended = createUser("bad_user", Role.USER);
        long byHer = post(loggedInAs("bad_user"), "停止されるユーザーの投稿");
        jdbc.update("update users set suspended = true where id = ?", suspended.getId());

        taro.get("/api/timeline")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(shown))
                .andExpect(jsonPath("$.items[0].replyCount").value(1));
        taro.get("/api/posts/" + hidden).andExpect(jsonPath("$.unavailable").value(false))
                .andExpect(jsonPath("$.hidden").value(true));
        taro.get("/api/posts/" + byHer).andExpect(jsonPath("$.unavailable").value(true));
    }

    @Test
    void タイムラインのSQLの回数は_投稿の数や画像の数によらず一定_N_plus_1にならない() throws Exception {
        // 投稿者 20 人が、それぞれ画像付きで投稿する
        for (int i = 0; i < 20; i++) {
            createUser("user" + i, Role.USER);
            loggedInAs("user" + i).multipart("/api/posts",
                    List.of(TestImages.image("png", 2, 2), TestImages.image("png", 3, 3)), "text", "投稿" + i);
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        taro.get("/api/timeline")
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].images.length()").value(2))
                .andExpect(jsonPath("$.items[19].author.handle").value("user0"));
        // ログイン中のユーザーの確認 1 + 投稿(投稿者つき)1 + 画像 1 + 返信の数 1
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
