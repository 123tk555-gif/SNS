package com.example.sns.auth;

import com.example.sns.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** メールのリンクに入れる値を作る・確かめる。値は推測できない 32 バイトの乱数で、1 回だけ使える。 */
@Component
class EmailTokens {

    static final String INVALID_LINK = "このリンクは期限切れか、すでに使われています";

    private final SecureRandom random = new SecureRandom();
    private final EmailTokenRepository repository;

    EmailTokens(EmailTokenRepository repository) {
        this.repository = repository;
    }

    /** 新しい値を作って保存し、メールに入れる元の値を返す */
    String issue(User user, EmailToken.Purpose purpose, Duration validFor) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new EmailToken(user, purpose, hash(token), Instant.now().plus(validFor)));
        return token;
    }

    /** 値を確かめて使用済みにし、その値のユーザーを返す。期限切れ・使用済み・存在しないときは 400 */
    User consume(String token, EmailToken.Purpose purpose) {
        Instant now = Instant.now();
        EmailToken found = repository.findByTokenHashAndPurpose(hash(token), purpose)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK));
        found.markUsed(now);
        return found.getUser();
    }

    boolean issuedWithin(User user, EmailToken.Purpose purpose, Duration within) {
        return repository.existsByUserAndPurposeAndCreatedAtAfter(user, purpose, Instant.now().minus(within));
    }

    void deleteAllFor(User user) {
        repository.deleteByUser(user);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
