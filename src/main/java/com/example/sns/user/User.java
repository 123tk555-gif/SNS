package com.example.sns.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** ユーザー(機能定義書 4 の users テーブル)。 */
@Entity
@Table(name = "users")
public class User {

    /** ログインの失敗がこの回数続いたら、しばらくロックする */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 小文字にして保存する */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt で変換したパスワード。Google だけで登録した人は null */
    @Column(length = 100)
    private String passwordHash;

    @Column(unique = true)
    private String googleSub;

    @Column(nullable = false)
    private boolean emailVerified;

    /** ユーザーID(入力したとおりの大文字・小文字)。初期設定前は null */
    @Column(length = 15)
    private String handle;

    /** 重複を大文字・小文字の区別なく確かめるための、小文字にしたユーザーID */
    @Column(length = 15, unique = true)
    private String handleLower;

    @Column(length = 50)
    private String displayName;

    @Column(length = 160)
    private String bio;

    private String avatarPath;

    private String headerPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean suspended;

    @Column(nullable = false)
    private int failedLoginCount;

    /** この日時までログインを受け付けない */
    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(String email) {
        this.email = normalizeEmail(email);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** メールアドレスとパスワードで登録した、確認前のユーザー */
    public static User withPassword(String email, String passwordHash) {
        User user = new User(email);
        user.passwordHash = passwordHash;
        return user;
    }

    /** Google で登録したユーザー(Google 側でメールアドレスが確認済みなので、確認済みにする) */
    public static User withGoogle(String email, String googleSub) {
        User user = new User(email);
        user.googleSub = googleSub;
        user.emailVerified = true;
        return user;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeHandle(String handle) {
        return handle.toLowerCase(Locale.ROOT);
    }

    public boolean isOnboarded() {
        return handle != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** ログインに失敗した。続けて MAX_FAILED_LOGINS 回失敗したら LOCK_DURATION だけロックする */
    public void recordFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLoginCount = 0;
        }
        touch();
    }

    public void recordSuccessfulLogin() {
        if (failedLoginCount != 0 || lockedUntil != null) {
            failedLoginCount = 0;
            lockedUntil = null;
            touch();
        }
    }

    public void verifyEmail() {
        emailVerified = true;
        touch();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        failedLoginCount = 0;
        lockedUntil = null;
        touch();
    }

    public void linkGoogle(String googleSub) {
        this.googleSub = googleSub;
        touch();
    }

    public void completeOnboarding(String handle, String displayName) {
        this.handle = handle;
        this.handleLower = normalizeHandle(handle);
        this.displayName = displayName;
        touch();
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
        touch();
    }

    /** テストデータ(プロファイル seed)用: 管理者にする */
    public void setRole(Role role) {
        this.role = role;
        touch();
    }

    public void setBio(String bio) {
        this.bio = bio;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getHandle() {
        return handle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public String getHeaderPath() {
        return headerPath;
    }

    public Role getRole() {
        return role;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
