package com.example.sns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** お試しログイン(F-AUTH-08) */
class GuestLoginTest extends ApiTestSupport {

    @Autowired
    GuestService guests;

    @Autowired
    AuthService auth;

    private Browser guestBrowser() throws Exception {
        Browser browser = browser();
        browser.post("/api/auth/guest", "{}").andExpect(status().isOk());
        return browser;
    }

    private long postAs(Browser browser, String text) throws Exception {
        String body = browser.multipart("/api/posts", List.of(), "text", text)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 画面の設定で_お試しログインが使えることがわかる() throws Exception {
        browser().get("/api/auth/config").andExpect(jsonPath("$.guestEnabled").value(true));
    }

    @Test
    void 登録せずに_お試しのアカウントでログインでき_何度押しても同じアカウント() throws Exception {
        Browser first = browser();
        String body = first.post("/api/auth/guest", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.handle").value("guest"))
                .andExpect(jsonPath("$.onboarded").value(true))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        first.get("/api/auth/me").andExpect(jsonPath("$.id").value(id));

        browser().post("/api/auth/guest", "{}").andExpect(jsonPath("$.id").value(id));
        assertThat(count("select count(*) from users where guest")).isEqualTo(1);
    }

    @Test
    void ふつうのアカウントはguestではない() throws Exception {
        createUser("taro", Role.USER);
        loggedInAs("taro").get("/api/auth/me").andExpect(jsonPath("$.guest").value(false));
    }

    @Test
    void お試しのアカウントは_投稿できるが_プロフィールと管理者の機能は使えない() throws Exception {
        Browser guest = guestBrowser();
        postAs(guest, "お試しの投稿です");

        guest.put("/api/me/profile", "{\"displayName\":\"いたずら\",\"bio\":\"\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(GuestService.CANNOT_EDIT));
        guest.delete("/api/me/avatar").andExpect(status().isForbidden());
        guest.get("/api/admin/users").andExpect(status().isNotFound());
    }

    @Test
    void ユーザーIDのguestは_ふつうの登録では使えない() throws Exception {
        assertThat(auth.handleProblem("guest")).isEqualTo(AuthService.HANDLE_RESERVED);
    }

    @Test
    void 毎日の片付けで_お試しのアカウントの書き込みだけが消える() throws Exception {
        User taro = createUser("taro", Role.USER);
        Browser taroBrowser = loggedInAs("taro");
        long taroPost = postAs(taroBrowser, "たろうの投稿");
        Browser guest = guestBrowser();
        long guestPost = postAs(guest, "お試しの投稿");
        guest.put("/api/posts/" + taroPost + "/like", "{}").andExpect(status().isOk());
        guest.put("/api/users/taro/follow", "{}").andExpect(status().isOk());
        taroBrowser.put("/api/posts/" + guestPost + "/like", "{}").andExpect(status().isOk());

        guests.cleanup();

        assertThat(count("select count(*) from posts where id = ? and deleted_at is not null", guestPost)).isEqualTo(1);
        assertThat(count("select count(*) from posts where id = ? and deleted_at is null", taroPost)).isEqualTo(1);
        long guestId = users.findFirstByGuestTrueOrderByIdAsc().orElseThrow().getId();
        assertThat(count("select count(*) from likes where user_id = ? or post_id = ?", guestId, guestPost)).isZero();
        assertThat(count("select count(*) from follows where from_user_id = ?", guestId)).isZero();
        // ほかの人のデータは残る
        assertThat(users.findById(taro.getId())).isPresent();
        // お試しのアカウント自体は残り、またログインできる
        guestBrowser().get("/api/auth/me").andExpect(jsonPath("$.guest").value(true));
    }
}
