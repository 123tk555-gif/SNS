package com.example.sns.auth;

import com.example.sns.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 確認メールとパスワード再設定メールを送る。開発中は Mailpit(http://localhost:8025)に届く。 */
@Component
class AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(AuthMailer.class);

    private final JavaMailSender sender;
    private final String from;
    private final String baseUrl;

    AuthMailer(JavaMailSender sender, @Value("${sns.mail.from}") String from, @Value("${sns.base-url}") String baseUrl) {
        this.sender = sender;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    void sendVerification(User user, String token) {
        send(user, "【SNS】メールアドレスの確認", """
                SNSタイムラインアプリへのご登録ありがとうございます。

                次のリンクを開いて、登録を完了してください(リンクの有効期限は24時間です)。
                %s/verify-email?token=%s

                お心当たりがない場合は、このメールを破棄してください。
                """.formatted(baseUrl, token));
    }

    void sendPasswordReset(User user, String token) {
        send(user, "【SNS】パスワードの再設定", """
                パスワードの再設定を受け付けました。

                次のリンクを開いて、新しいパスワードを設定してください(リンクの有効期限は1時間です)。
                %s/reset-password?token=%s

                お心当たりがない場合は、このメールを破棄してください。パスワードは変わりません。
                """.formatted(baseUrl, token));
    }

    private void send(User user, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("メールを送れませんでした(ユーザー {})", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "メールを送れませんでした。しばらくしてからもう一度お試しください");
        }
    }
}
