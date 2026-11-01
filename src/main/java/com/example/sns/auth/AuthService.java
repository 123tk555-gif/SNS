package com.example.sns.auth;

import com.example.sns.security.SessionKiller;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** アカウントの登録・確認・ログイン・パスワード再設定・初期設定(機能定義書 3.1)。 */
@Service
@Transactional
public class AuthService {

    static final Duration VERIFY_VALID_FOR = Duration.ofHours(24);
    static final Duration RESET_VALID_FOR = Duration.ofHours(1);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    static final String BAD_CREDENTIALS = "メールアドレスまたはパスワードが違います";
    static final String NOT_VERIFIED = "メールアドレスの確認が済んでいません";
    static final String SUSPENDED = "このアカウントは利用停止中です";
    static final String LOCKED = "ログインの失敗が続いたため、15分間ログインできません";
    static final String EMAIL_TAKEN = "このメールアドレスは登録済みです";
    static final String PASSWORD_RULE = "パスワードは8文字以上72文字以内で入力してください";
    static final String HANDLE_RULE = "ユーザーIDは半角英数字と_の3〜15文字で入力してください";
    static final String HANDLE_RESERVED = "このユーザーIDは使えません";
    static final String HANDLE_TAKEN = "このユーザーIDは使われています";

    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9_]{3,15}");
    /** 管理者や運営と見分けがつかなくなるため、ユーザーIDに使えない語 */
    private static final Set<String> RESERVED_HANDLES =
            Set.of("admin", "administrator", "root", "system", "support", "official");
    /** BCrypt が扱えるのは 72 バイトまで */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final EmailTokens tokens;
    private final AuthMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final SessionKiller sessionKiller;
    /** 存在しないメールアドレスでも同じだけ時間をかけるための、照合用のダミー */
    private final String dummyHash;

    public AuthService(UserRepository users, EmailTokens tokens, AuthMailer mailer, PasswordEncoder passwordEncoder,
            SessionKiller sessionKiller) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.sessionKiller = sessionKiller;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    // ---- 登録とメールアドレスの確認(F-AUTH-01) ----

    public void signup(String email, String password) {
        requireValidPassword(password);
        String normalized = User.normalizeEmail(email);
        Optional<User> existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            User old = existing.get();
            // 確認しないまま 24 時間たったユーザーは、同じメールアドレスで登録し直せる
            if (old.isEmailVerified() || old.getCreatedAt().isAfter(Instant.now().minus(VERIFY_VALID_FOR))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_TAKEN);
            }
            tokens.deleteAllFor(old);
            users.delete(old);
            users.flush();
        }
        User user = users.save(User.withPassword(normalized, passwordEncoder.encode(password)));
        mailer.sendVerification(user, tokens.issue(user, EmailToken.Purpose.VERIFY_EMAIL, VERIFY_VALID_FOR));
    }

    public User verifyEmail(String token) {
        User user = tokens.consume(token, EmailToken.Purpose.VERIFY_EMAIL);
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SUSPENDED);
        }
        user.verifyEmail();
        return user;
    }

    /** 確認メールの再送。登録されていない・確認済みのときも、何もせずに成功を返す(登録の有無を知られないため) */
    public void resendVerification(String email) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email)).filter(u -> !u.isEmailVerified());
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (tokens.issuedWithin(user, EmailToken.Purpose.VERIFY_EMAIL, RESEND_INTERVAL)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "確認メールの再送は、1分に1回までです");
        }
        mailer.sendVerification(user, tokens.issue(user, EmailToken.Purpose.VERIFY_EMAIL, VERIFY_VALID_FOR));
    }

    // ---- ログイン(F-AUTH-03, F-AUTH-07) ----

    /** 失敗の回数は、例外で終わっても保存する(noRollbackFor) */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User login(String email, String password) {
        Instant now = Instant.now();
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        if (found.isPresent() && found.get().isLocked(now)) {
            throw new ResponseStatusException(HttpStatus.LOCKED, LOCKED);
        }
        if (!passwordMatches(found, password)) {
            found.ifPresent(user -> user.recordFailedLogin(now));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        // パスワードが合っているときだけ、確認前・停止中であることを伝える
        User user = found.get();
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_VERIFIED);
        }
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SUSPENDED);
        }
        user.recordSuccessfulLogin();
        return user;
    }

    private boolean passwordMatches(Optional<User> found, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            return false;
        }
        String hash = found.map(User::getPasswordHash).orElse(null);
        if (hash == null) {
            passwordEncoder.matches(password, dummyHash);
            return false;
        }
        return passwordEncoder.matches(password, hash);
    }

    // ---- Google ログイン(F-AUTH-02) ----

    /**
     * Google のユーザーに当たるユーザーを返す。Google の ID → 同じメールアドレス(Google 側で確認済みのときだけ)
     * → 新規作成 の順に探す。同じメールアドレスのユーザーがいるのに Google 側で未確認のときは空を返す。
     */
    public Optional<User> findOrCreateGoogleUser(String googleSub, String email, boolean emailVerifiedByGoogle) {
        Optional<User> bySub = users.findByGoogleSub(googleSub);
        if (bySub.isPresent()) {
            return bySub;
        }
        String normalized = User.normalizeEmail(email);
        Optional<User> byEmail = users.findByEmail(normalized);
        if (byEmail.isPresent()) {
            if (!emailVerifiedByGoogle) {
                return Optional.empty();
            }
            User user = byEmail.get();
            user.linkGoogle(googleSub);
            if (!user.isEmailVerified()) {
                user.verifyEmail();
            }
            return Optional.of(user);
        }
        return Optional.of(users.save(User.withGoogle(normalized, googleSub)));
    }

    // ---- パスワード再設定(F-AUTH-04) ----

    /** 登録の有無にかかわらず、同じ結果を返す。送るのは、パスワードを持つユーザーで、1 分以内に送っていないときだけ */
    public void requestPasswordReset(String email) {
        users.findByEmail(User.normalizeEmail(email))
                .filter(user -> user.getPasswordHash() != null)
                .filter(user -> !tokens.issuedWithin(user, EmailToken.Purpose.RESET_PASSWORD, RESEND_INTERVAL))
                .ifPresent(user -> mailer.sendPasswordReset(user,
                        tokens.issue(user, EmailToken.Purpose.RESET_PASSWORD, RESET_VALID_FOR)));
    }

    /** 新しいパスワードにし、そのユーザーのセッションを(今の画面以外)すべて消す */
    public void resetPassword(String token, String password, String currentSessionId) {
        requireValidPassword(password);
        User user = tokens.consume(token, EmailToken.Purpose.RESET_PASSWORD);
        user.changePassword(passwordEncoder.encode(password));
        sessionKiller.killAll(user.getId(), currentSessionId);
    }

    // ---- 初期設定(F-AUTH-05) ----

    public User completeOnboarding(long userId, String handle, String displayName) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください"));
        if (user.isOnboarded()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "初期設定は完了しています");
        }
        String problem = handleProblem(handle);
        if (problem != null) {
            HttpStatus status = HANDLE_TAKEN.equals(problem) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, problem);
        }
        user.completeOnboarding(handle, displayName.strip());
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // 確かめてから保存するまでの間に、同じユーザーIDを他の人が使った
            throw new ResponseStatusException(HttpStatus.CONFLICT, HANDLE_TAKEN);
        }
        return user;
    }

    /** ユーザーIDが使えないときはその理由を、使えるときは null を返す */
    @Transactional(readOnly = true)
    public String handleProblem(String handle) {
        if (handle == null || !HANDLE.matcher(handle).matches()) {
            return HANDLE_RULE;
        }
        String normalized = User.normalizeHandle(handle);
        if (RESERVED_HANDLES.contains(normalized)) {
            return HANDLE_RESERVED;
        }
        if (users.existsByHandleLower(normalized)) {
            return HANDLE_TAKEN;
        }
        return null;
    }

    private static void requireValidPassword(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 72 || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PASSWORD_RULE);
        }
    }
}
