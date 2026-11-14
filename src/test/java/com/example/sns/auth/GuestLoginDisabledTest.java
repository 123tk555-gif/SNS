package com.example.sns.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** お試しログインを止めたとき(GUEST_LOGIN_ENABLED=false) */
@TestPropertySource(properties = "sns.guest.enabled=false")
class GuestLoginDisabledTest extends ApiTestSupport {

    @Test
    void 止めているときは_画面にボタンを出させず_APIも404() throws Exception {
        browser().get("/api/auth/config").andExpect(jsonPath("$.guestEnabled").value(false));
        browser().post("/api/auth/guest", "{}").andExpect(status().isNotFound());
    }
}
