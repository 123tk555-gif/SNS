package com.example.sns.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.TestcontainersConfiguration;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cookie の属性(NF-SEC-04)は、本物のサーバー(Tomcat)を起動して確かめる。
 * MockMvc ではセッションの Cookie の設定(HttpOnly・SameSite・有効期限)が反映されないため。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sns.google.client-id=", "sns.google.client-secret="})
class CookieAttributesTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void CSRF対策のCookieは画面から読め_ログインのCookieは読めない() throws Exception {
        jdbc.update("delete from email_tokens");
        jdbc.update("delete from users");
        User user = User.withPassword("cookie@example.com", passwordEncoder.encode("password123"));
        user.verifyEmail();
        user.completeOnboarding("cookie_user", "クッキー");
        users.save(user);

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> config = client.send(
                HttpRequest.newBuilder(uri("/api/auth/config")).build(), HttpResponse.BodyHandlers.ofString());
        String xsrfCookie = setCookie(config, "XSRF-TOKEN");
        assertThat(xsrfCookie).doesNotContain("HttpOnly");
        String xsrf = xsrfCookie.substring("XSRF-TOKEN=".length(), xsrfCookie.indexOf(';'));

        HttpResponse<String> login = client.send(HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + xsrf)
                        .header("X-XSRF-TOKEN", xsrf)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"cookie@example.com\",\"password\":\"password123\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(setCookie(login, "SESSION"))
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=2592000");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String setCookie(HttpResponse<?> response, String name) {
        List<String> values = response.headers().allValues("Set-Cookie");
        return values.stream().filter(v -> v.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError(name + " の Cookie がありません: " + values));
    }
}
