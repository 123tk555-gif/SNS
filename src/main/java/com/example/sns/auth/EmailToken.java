package com.example.sns.auth;

import com.example.sns.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 確認メール・パスワード再設定メールのリンクに入れる値(機能定義書 4 の email_tokens テーブル)。 */
@Entity
@Table(name = "email_tokens")
public class EmailToken {

    public enum Purpose {
        VERIFY_EMAIL,
        RESET_PASSWORD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    /** 値を SHA-256 で変換したもの(元の値は保存しない) */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected EmailToken() {
    }

    EmailToken(User user, Purpose purpose, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    void markUsed(Instant now) {
        usedAt = now;
    }

    User getUser() {
        return user;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
