package com.example.sns.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * ログ(機能定義書「ログ」): リクエスト番号・アクセスログ・出来事のログ。
 * ログは Logback の ListAppender で受け取って、項目を確かめる(ファイルの JSON の形は、Spring Boot の機能)
 */
class RequestLogTest extends ApiTestSupport {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureLogs() {
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void stopCapture() {
        root.detachAppender(appender);
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        List<KeyValuePair> pairs = Objects.requireNonNullElse(event.getKeyValuePairs(), List.of());
        return pairs.stream().collect(Collectors.toMap(p -> p.key, p -> p.value, (a, b) -> b));
    }

    private List<ILoggingEvent> events(String action) {
        return appender.list.stream().filter(e -> action.equals(fields(e).get(Events.ACTION))).toList();
    }

    /** ログに出たすべての文字(本文・項目・MDC) */
    private String everything() {
        return appender.list.stream()
                .map(e -> e.getFormattedMessage() + " " + fields(e) + " " + e.getMDCPropertyMap())
                .collect(Collectors.joining("\n"));
    }

    @Test
    void APIのリクエストごとに番号を付け_アクセスログを1行出す() throws Exception {
        User taro = createUser("taro", Role.USER);
        Browser browser = loggedInAs("taro");
        appender.list.clear();

        MockHttpServletResponse response = browser.get("/api/search/posts?q=ひみつの言葉")
                .andExpect(status().isOk()).andReturn().getResponse();

        String requestId = response.getHeader(RequestLogFilter.HEADER);
        assertThat(requestId).matches("[0-9a-f]{12}");
        List<ILoggingEvent> access = events("http.request");
        assertThat(access).singleElement().satisfies(event -> {
            Map<String, Object> f = fields(event);
            assertThat(f).containsEntry("http.request.method", "GET")
                    .containsEntry("url.path", "/api/search/posts")
                    .containsEntry("http.route", "/api/search/posts")
                    .containsEntry("http.response.status_code", 200)
                    .containsKey("event.duration_ms")
                    .containsKey("client.ip");
            assertThat(event.getMDCPropertyMap())
                    .containsEntry(RequestLogFilter.REQUEST_ID, requestId)
                    .containsEntry(RequestLogFilter.USER_ID, taro.getId().toString());
            assertThat(event.getLoggerName()).isEqualTo("com.example.sns.access");
        });
        // 検索の言葉(クエリ文字列)はログに残さない
        assertThat(everything()).doesNotContain("ひみつ").doesNotContain("q=");
    }

    @Test
    void ルートはIDを含まない形で残し_ログインしていなければユーザーIDは付かない() throws Exception {
        browser().get("/api/posts/12345").andExpect(status().isUnauthorized());

        assertThat(events("http.request")).singleElement().satisfies(event -> {
            assertThat(fields(event)).containsEntry("http.response.status_code", 401)
                    .containsEntry("url.path", "/api/posts/12345");
            assertThat(event.getMDCPropertyMap()).doesNotContainKey(RequestLogFilter.USER_ID);
        });

        createUser("taro", Role.USER);
        Browser browser = loggedInAs("taro");
        appender.list.clear();
        browser.get("/api/posts/12345").andExpect(status().isNotFound());
        assertThat(events("http.request")).singleElement()
                .satisfies(event -> assertThat(fields(event)).containsEntry("http.route", "/api/posts/{id}"));
    }

    @Test
    void 画面のファイルはアクセスログを出さない() throws Exception {
        browser().get("/login");
        assertThat(events("http.request")).isEmpty();
    }

    @Test
    void ログインの成功と失敗を残し_メールアドレスとパスワードは残さない() throws Exception {
        User taro = createUser("taro", Role.USER);

        browser().post("/api/auth/login", loginJson("taro@example.com", "wrong-password-xyz"))
                .andExpect(status().isUnauthorized());
        browser().post("/api/auth/login", loginJson("nobody@example.com", "wrong-password-xyz"))
                .andExpect(status().isUnauthorized());
        loggedInAs("taro");

        List<ILoggingEvent> failed = events("auth.login_failed");
        assertThat(failed).hasSize(2);
        assertThat(fields(failed.get(0))).containsEntry("reason", "bad_credentials")
                .containsEntry("accountId", taro.getId());
        // 登録されていないメールアドレスでは、アカウントの ID もない
        assertThat(fields(failed.get(1))).containsEntry("reason", "bad_credentials").doesNotContainKey("accountId");
        assertThat(events("auth.login")).singleElement()
                .satisfies(event -> assertThat(fields(event)).containsEntry("accountId", taro.getId())
                        .containsEntry("method", "password"));

        assertThat(everything()).doesNotContain("example.com").doesNotContain("wrong-password").doesNotContain(PASSWORD);
    }

    @Test
    void ログインの失敗が続いてロックしたことを残す() throws Exception {
        User taro = createUser("taro", Role.USER);
        for (int i = 0; i < User.MAX_FAILED_LOGINS; i++) {
            browser().post("/api/auth/login", loginJson("taro@example.com", "wrong-password-xyz"));
        }
        browser().post("/api/auth/login", loginJson("taro@example.com", PASSWORD)).andExpect(status().isLocked());

        assertThat(events("auth.locked")).singleElement()
                .satisfies(event -> assertThat(fields(event)).containsEntry("accountId", taro.getId()));
        assertThat(events("auth.login_failed")).last()
                .satisfies(event -> assertThat(fields(event)).containsEntry("reason", "locked"));
    }

    @Test
    void メールを送れなかった登録は_登録のログを残さず_メールの失敗をERRORで残す() throws Exception {
        doThrow(new MailSendException("メールのサーバーが止まっている")).when(mailSender).send(any(SimpleMailMessage.class));

        browser().post("/api/auth/signup", "{\"email\":\"new@example.com\",\"password\":\"password123\"}")
                .andExpect(status().isServiceUnavailable());

        assertThat(events("auth.signup")).isEmpty();
        assertThat(events("mail.failed")).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(fields(event)).containsKey("accountId");
        });
        assertThat(everything()).doesNotContain("new@example.com");
    }

    @Test
    void 管理者の操作を残す() throws Exception {
        User admin = createUser("admin_user", Role.ADMIN);
        User taro = createUser("taro", Role.USER);
        Browser browser = loggedInAs("admin_user");

        browser.put("/api/admin/users/" + taro.getId() + "/suspension", "{\"suspended\":true}").andExpect(status().isOk());

        assertThat(events("admin.user_suspended")).singleElement().satisfies(event -> {
            assertThat(fields(event)).containsEntry("adminId", admin.getId()).containsEntry("accountId", taro.getId());
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestLogFilter.USER_ID, admin.getId().toString());
        });
    }

    @Test
    void 決まったエラーは今までどおりの状態で返す() throws Exception {
        createUser("taro", Role.USER);
        // GET しかない API に PUT → 405(500 にしない)
        loggedInAs("taro").put("/api/timeline", "{}").andExpect(status().isMethodNotAllowed());
        assertThat(events("http.unexpected_error")).isEmpty();
    }
}
