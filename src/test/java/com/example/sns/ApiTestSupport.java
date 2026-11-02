package com.example.sns;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API のテストの共通部分。テスト専用のデータベースを使い、各テストの前にデータを空にする。
 * メールは実際には送らず、送ろうとした内容をあとから取り出せるようにする。
 */
@Import(TestcontainersConfiguration.class)
// .env に Google ログインの設定があっても、テストでは使わない。画像はテスト用のフォルダに保存する。
// SQL の回数を数えられるよう、Hibernate の統計を有効にする(N+1 のテスト用)
@SpringBootTest(properties = {
        "sns.google.client-id=", "sns.google.client-secret=",
        "sns.upload-dir=target/test-uploads",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
})
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from post_images");
        jdbc.update("delete from posts");
        jdbc.update("delete from email_tokens");
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    protected Browser browser() {
        return new Browser(mvc);
    }

    /** 確認済み・初期設定済みのユーザーを直接作る(パスワードは PASSWORD) */
    protected User createUser(String handle, Role role) {
        User user = User.withPassword(handle + "@example.com", passwordEncoder.encode(PASSWORD));
        user.verifyEmail();
        user.completeOnboarding(handle, handle + "さん");
        user.setRole(role);
        return users.save(user);
    }

    protected Browser loggedInAs(String handle) throws Exception {
        Browser browser = browser();
        browser.post("/api/auth/login", loginJson(handle + "@example.com", PASSWORD)).andExpect(status().isOk());
        return browser;
    }

    protected static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    protected List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    /** 最後に送ったメールのリンクから、token= の値を取り出す */
    protected String tokenFromLastMail() {
        List<SimpleMailMessage> mails = sentMails();
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mails.getLast().getText());
        if (!matcher.find()) {
            throw new AssertionError("メールにリンクがありません");
        }
        return matcher.group(1);
    }
}
