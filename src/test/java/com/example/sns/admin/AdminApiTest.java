package com.example.sns.admin;

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

/** 管理者の投稿管理・ユーザー一覧(機能定義書 3.12。F-ADMIN-01〜05)。 */
class AdminApiTest extends ApiTestSupport {

    private Browser admin;
    private Browser taro;
    private long taroId;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin_user", Role.ADMIN);
        taroId = createUser("taro", Role.USER).getId();
        admin = loggedInAs("admin_user");
        taro = loggedInAs("taro");
    }

    private long post(Browser browser, String text) throws Exception {
        String body = browser.multipart("/api/posts", List.of(), "text", text)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 管理者以外は投稿の管理APIを使えない() throws Exception {
        long id = post(taro, "ふつうの投稿");
        taro.get("/api/admin/posts").andExpect(status().isNotFound());
        taro.put("/api/admin/posts/" + id + "/visibility", "{\"hidden\":true}").andExpect(status().isNotFound());
        browser().get("/api/admin/posts").andExpect(status().isUnauthorized());
        taro.get("/api/posts/" + id).andExpect(jsonPath("$.hidden").value(false));
    }

    @Test
    void 投稿の一覧は本文で探せて_非表示や停止中の人の投稿にも印が付く() throws Exception {
        long visible = post(taro, "カフェに行きました");
        long hidden = post(taro, "100% 本気の投稿_です");
        long deleted = post(taro, "消す投稿");
        taro.delete("/api/posts/" + deleted).andExpect(status().isNoContent());
        admin.put("/api/admin/posts/" + hidden + "/visibility", "{\"hidden\":true}").andExpect(status().isOk());

        admin.get("/api/admin/posts").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(hidden))
                .andExpect(jsonPath("$.items[0].hidden").value(true))
                .andExpect(jsonPath("$.items[1].id").value(visible))
                .andExpect(jsonPath("$.items[1].hidden").value(false))
                .andExpect(jsonPath("$.items[1].authorHandle").value("taro"));
        admin.get("/api/admin/posts?q=CAF%C3%89").andExpect(jsonPath("$.total").value(0));
        admin.get("/api/admin/posts?q=%E3%82%AB%E3%83%95%E3%82%A7").andExpect(jsonPath("$.total").value(1));
        // % と _ は、ふつうの文字として探す
        admin.get("/api/admin/posts?q=100%25").andExpect(jsonPath("$.total").value(1));
        admin.get("/api/admin/posts?q=%25").andExpect(jsonPath("$.total").value(1));
        admin.get("/api/admin/posts?q=_").andExpect(jsonPath("$.total").value(1));

        admin.put("/api/admin/users/" + taroId + "/suspension", "{\"suspended\":true}").andExpect(status().isOk());
        admin.get("/api/admin/posts").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].authorSuspended").value(true));
    }

    @Test
    void 非表示にすると他の人から消え_本人には印付きで見え_戻せる() throws Exception {
        createUser("hanako", Role.USER);
        Browser hanako = loggedInAs("hanako");
        long id = post(taro, "非表示にされる投稿");
        hanako.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(1));

        admin.put("/api/admin/posts/" + id + "/visibility", "{\"hidden\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true));
        hanako.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(0));
        hanako.get("/api/users/taro/posts").andExpect(jsonPath("$.items.length()").value(0));
        taro.get("/api/users/taro/posts").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].hidden").value(true));
        hanako.get("/api/posts/" + id).andExpect(jsonPath("$.unavailable").value(true));

        admin.put("/api/admin/posts/" + id + "/visibility", "{\"hidden\":false}").andExpect(status().isOk());
        hanako.get("/api/timeline").andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 非表示にすると_接続中の画面にpost_deletedが届く() throws Exception {
        long id = post(taro, "消される投稿");
        createUser("hanako", Role.USER);
        MockHttpServletResponse stream = loggedInAs("hanako").get("/api/stream")
                .andExpect(request().asyncStarted()).andReturn().getResponse();
        admin.put("/api/admin/posts/" + id + "/visibility", "{\"hidden\":true}").andExpect(status().isOk());
        String received = stream.getContentAsString(StandardCharsets.UTF_8);
        assertThat(received).contains("event:post-deleted").contains("\"id\":" + id);
    }

    @Test
    void 入力が足りない_存在しない投稿は400と404() throws Exception {
        long id = post(taro, "投稿");
        admin.put("/api/admin/posts/" + id + "/visibility", "{}").andExpect(status().isBadRequest());
        admin.put("/api/admin/posts/999999/visibility", "{\"hidden\":true}").andExpect(status().isNotFound());
        taro.delete("/api/posts/" + id).andExpect(status().isNoContent());
        admin.put("/api/admin/posts/" + id + "/visibility", "{\"hidden\":true}").andExpect(status().isNotFound());
    }

    @Test
    void ユーザーの一覧には投稿数が出る_削除した投稿は数えない() throws Exception {
        post(taro, "1");
        post(taro, "2");
        long gone = post(taro, "3");
        taro.delete("/api/posts/" + gone).andExpect(status().isNoContent());
        admin.get("/api/admin/users?q=taro").andExpect(jsonPath("$.items[0].postCount").value(2));
        admin.get("/api/admin/users?q=admin_user").andExpect(jsonPath("$.items[0].postCount").value(0));
    }

    @Test
    void 自分自身と管理者は停止できない() throws Exception {
        long adminId = ((Number) JsonPath.read(
                admin.get("/api/auth/me").andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        admin.put("/api/admin/users/" + adminId + "/suspension", "{\"suspended\":true}")
                .andExpect(status().isBadRequest());
    }
}
