package com.example.sns.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import org.junit.jupiter.api.Test;

/** URL ごとの許可・CSRF 対策・管理者の API・利用停止(機能定義書 1.2・2・3.12)。 */
class AccessControlTest extends ApiTestSupport {

    @Test
    void ログインしていないと_ログインが必要なAPIは401() throws Exception {
        browser().get("/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SecurityConfig.LOGIN_REQUIRED));
        browser().get("/api/admin/users").andExpect(status().isUnauthorized());
        browser().get("/media/avatars/a.webp").andExpect(status().isUnauthorized());
    }

    @Test
    void 誰でも使えるAPIは_ログインなしで使える() throws Exception {
        browser().get("/api/auth/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEnabled").value(false));
    }

    @Test
    void 画面のURLはReactのindex_htmlを返し_APIとファイルは対象外() throws Exception {
        for (String url : new String[] {"/", "/login", "/no-such-page", "/users/taro/following"}) {
            browser().get(url).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        browser().get("/assets/missing.js").andExpect(status().isNotFound());
        createUser("taro", Role.USER);
        loggedInAs("taro").get("/api/no-such-api").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ページが見つかりません"));
    }

    @Test
    void CSRF対策の値がない変更の操作は403() throws Exception {
        createUser("taro", Role.USER);
        browser().postWithoutCsrf("/api/auth/login", loginJson("taro@example.com", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SecurityConfig.CSRF_FAILED));
    }

    @Test
    void 管理者以外が管理者のAPIを呼ぶと_存在しないURLと同じ404() throws Exception {
        createUser("taro", Role.USER);
        Browser user = loggedInAs("taro");
        user.get("/api/admin/users")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ページが見つかりません"));
        user.get("/api/no-such-api").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ページが見つかりません"));
    }

    @Test
    void 管理者はユーザーの一覧を見られる() throws Exception {
        createUser("admin_user", Role.ADMIN);
        createUser("taro", Role.USER);
        loggedInAs("admin_user").get("/api/admin/users?q=TARO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].handle").value("taro"));
    }

    @Test
    void データベースで権限を変えると_ログインし直さなくても反映される() throws Exception {
        User taro = createUser("taro", Role.USER);
        Browser browser = loggedInAs("taro");
        browser.get("/api/admin/users").andExpect(status().isNotFound());
        jdbc.update("update users set role = 'ADMIN' where id = ?", taro.getId());
        browser.get("/api/admin/users").andExpect(status().isOk());
    }

    @Test
    void 利用停止にすると_そのユーザーはすぐにログアウトされ_ログインもできない() throws Exception {
        createUser("admin_user", Role.ADMIN);
        User taro = createUser("taro", Role.USER);
        Browser taroBrowser = loggedInAs("taro");
        Browser admin = loggedInAs("admin_user");

        admin.put("/api/admin/users/" + taro.getId() + "/suspension", "{\"suspended\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));

        taroBrowser.get("/api/auth/me").andExpect(status().isUnauthorized());
        browser().post("/api/auth/login", loginJson("taro@example.com", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("このアカウントは利用停止中です"));

        admin.put("/api/admin/users/" + taro.getId() + "/suspension", "{\"suspended\":false}")
                .andExpect(status().isOk());
        browser().post("/api/auth/login", loginJson("taro@example.com", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void 自分自身と他の管理者は利用停止にできない() throws Exception {
        User admin = createUser("admin_user", Role.ADMIN);
        User other = createUser("other_admin", Role.ADMIN);
        Browser browser = loggedInAs("admin_user");
        browser.put("/api/admin/users/" + admin.getId() + "/suspension", "{\"suspended\":true}")
                .andExpect(status().isBadRequest());
        browser.put("/api/admin/users/" + other.getId() + "/suspension", "{\"suspended\":true}")
                .andExpect(status().isBadRequest());
    }
}
