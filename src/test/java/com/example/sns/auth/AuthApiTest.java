package com.example.sns.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import org.junit.jupiter.api.Test;

/** アカウントの登録・確認・ログイン・パスワード再設定・初期設定(機能定義書 3.1)。 */
class AuthApiTest extends ApiTestSupport {

    private static final String NEW_EMAIL = "new@example.com";

    private void signup(Browser browser, String email) throws Exception {
        browser.post("/api/auth/signup", loginJson(email, PASSWORD)).andExpect(status().isCreated());
    }

    @Test
    void 登録_確認メールのリンク_初期設定_ログアウト_ログインまでの流れ() throws Exception {
        Browser browser = browser();
        signup(browser, "New@Example.com");
        assertThat(sentMails().getLast().getTo()).containsExactly(NEW_EMAIL);
        assertThat(sentMails().getLast().getText()).contains("/verify-email?token=");

        // リンクを開くと確認済みになり、そのままログイン状態になる(初期設定はまだ)
        browser.post("/api/auth/verify-email", "{\"token\":\"" + tokenFromLastMail() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.onboarded").value(false));

        browser.post("/api/me/onboarding", "{\"handle\":\"New_User\",\"displayName\":\"  あたらしい人  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("New_User"))
                .andExpect(jsonPath("$.displayName").value("あたらしい人"))
                .andExpect(jsonPath("$.onboarded").value(true))
                .andExpect(jsonPath("$.role").value("USER"));

        browser.post("/api/auth/logout", "{}").andExpect(status().isNoContent());
        browser.get("/api/auth/me").andExpect(status().isUnauthorized());

        browser.post("/api/auth/login", loginJson("NEW@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("New_User"));
        browser.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(NEW_EMAIL));
    }

    @Test
    void 登録_入力チェックと登録済みのメールアドレス() throws Exception {
        Browser browser = browser();
        browser.post("/api/auth/signup", loginJson("not-an-email", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        browser.post("/api/auth/signup", loginJson(NEW_EMAIL, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthService.PASSWORD_RULE));

        signup(browser, NEW_EMAIL);
        browser.post("/api/auth/signup", loginJson(NEW_EMAIL, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AuthService.EMAIL_TAKEN));
    }

    @Test
    void 確認前のユーザーは_パスワードが合っていれば403_違えば他と同じ401() throws Exception {
        Browser browser = browser();
        signup(browser, NEW_EMAIL);
        browser.post("/api/auth/login", loginJson(NEW_EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthService.NOT_VERIFIED));
        browser.post("/api/auth/login", loginJson(NEW_EMAIL, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthService.BAD_CREDENTIALS));
    }

    @Test
    void ログインの失敗は_存在しないメールアドレスでも同じ文() throws Exception {
        createUser("taro", Role.USER);
        Browser browser = browser();
        browser.post("/api/auth/login", loginJson("taro@example.com", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthService.BAD_CREDENTIALS));
        browser.post("/api/auth/login", loginJson("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthService.BAD_CREDENTIALS));
    }

    @Test
    void 五回続けて失敗するとロックされ_正しいパスワードでもログインできない() throws Exception {
        createUser("taro", Role.USER);
        Browser browser = browser();
        for (int i = 0; i < 5; i++) {
            browser.post("/api/auth/login", loginJson("taro@example.com", "wrong-password"))
                    .andExpect(status().isUnauthorized());
        }
        browser.post("/api/auth/login", loginJson("taro@example.com", PASSWORD))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(AuthService.LOCKED));

        // 15 分たてば、またログインできる
        jdbc.update("update users set locked_until = now() - interval '1 second'");
        browser.post("/api/auth/login", loginJson("taro@example.com", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void 確認リンクは1回だけ使え_期限切れは使えない() throws Exception {
        Browser browser = browser();
        signup(browser, NEW_EMAIL);
        String token = tokenFromLastMail();
        browser.post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isOk());
        browser().post("/api/auth/verify-email", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(EmailTokens.INVALID_LINK));

        signup(browser(), "other@example.com");
        String expired = tokenFromLastMail();
        jdbc.update("update email_tokens set expires_at = now() - interval '1 second' where used_at is null");
        browser().post("/api/auth/verify-email", "{\"token\":\"" + expired + "\"}")
                .andExpect(status().isBadRequest());
        browser().post("/api/auth/verify-email", "{\"token\":\"made-up-token\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 確認メールの再送は1分に1回まで() throws Exception {
        Browser browser = browser();
        signup(browser, NEW_EMAIL);
        browser.post("/api/auth/verify-email/resend", "{\"email\":\"" + NEW_EMAIL + "\"}")
                .andExpect(status().isTooManyRequests());

        jdbc.update("update email_tokens set created_at = now() - interval '61 seconds'");
        browser.post("/api/auth/verify-email/resend", "{\"email\":\"" + NEW_EMAIL + "\"}")
                .andExpect(status().isNoContent());
        assertThat(sentMails()).hasSize(2);

        // 登録されていないメールアドレスでも、同じように成功を返す(登録の有無を知られないため)
        browser.post("/api/auth/verify-email/resend", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void パスワード再設定_他の端末のログインが消え_新しいパスワードでログインできる() throws Exception {
        createUser("taro", Role.USER);
        Browser otherDevice = loggedInAs("taro");
        otherDevice.get("/api/auth/me").andExpect(status().isOk());

        Browser browser = browser();
        browser.post("/api/auth/password-reset", "{\"email\":\"taro@example.com\"}").andExpect(status().isNoContent());
        assertThat(sentMails().getLast().getText()).contains("/reset-password?token=");
        browser.post("/api/auth/password-reset/confirm",
                        "{\"token\":\"" + tokenFromLastMail() + "\",\"password\":\"new-password-1\"}")
                .andExpect(status().isNoContent());

        otherDevice.get("/api/auth/me").andExpect(status().isUnauthorized());
        browser.post("/api/auth/login", loginJson("taro@example.com", PASSWORD)).andExpect(status().isUnauthorized());
        browser.post("/api/auth/login", loginJson("taro@example.com", "new-password-1")).andExpect(status().isOk());
    }

    @Test
    void パスワード再設定は_登録されていないメールアドレスでも同じ結果でメールは送らない() throws Exception {
        browser().post("/api/auth/password-reset", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void 初期設定_ユーザーIDの形_予約語_重複() throws Exception {
        createUser("Taken_Id", Role.USER);
        Browser browser = browser();
        signup(browser, NEW_EMAIL);
        browser.post("/api/auth/verify-email", "{\"token\":\"" + tokenFromLastMail() + "\"}").andExpect(status().isOk());

        browser.post("/api/me/onboarding", "{\"handle\":\"ab\",\"displayName\":\"名前\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthService.HANDLE_RULE));
        browser.post("/api/me/onboarding", "{\"handle\":\"にほんご\",\"displayName\":\"名前\"}")
                .andExpect(status().isBadRequest());
        browser.post("/api/me/onboarding", "{\"handle\":\"Admin\",\"displayName\":\"名前\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthService.HANDLE_RESERVED));
        browser.post("/api/me/onboarding", "{\"handle\":\"taken_ID\",\"displayName\":\"名前\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AuthService.HANDLE_TAKEN));
        browser.post("/api/me/onboarding", "{\"handle\":\"ok_id\",\"displayName\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));

        browser.get("/api/handles/taken_id/availability")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value(AuthService.HANDLE_TAKEN));
        browser.get("/api/handles/free_id/availability").andExpect(jsonPath("$.available").value(true));

        browser.post("/api/me/onboarding", "{\"handle\":\"ok_id\",\"displayName\":\"名前\"}").andExpect(status().isOk());
        browser.post("/api/me/onboarding", "{\"handle\":\"again\",\"displayName\":\"名前\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 初期設定の前は_初期設定に必要なAPI以外は403() throws Exception {
        Browser browser = browser();
        signup(browser, NEW_EMAIL);
        browser.post("/api/auth/verify-email", "{\"token\":\"" + tokenFromLastMail() + "\"}").andExpect(status().isOk());

        browser.get("/api/auth/me").andExpect(status().isOk());
        browser.get("/api/posts").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("初期設定を完了してください"));
    }
}
