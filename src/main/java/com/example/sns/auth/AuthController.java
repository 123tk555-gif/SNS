package com.example.sns.auth;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.notification.NotificationService;
import com.example.sns.security.GoogleSettings;
import com.example.sns.logging.Events;
import com.example.sns.security.LoginUser;
import com.example.sns.security.SessionLogin;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 認証の API(機能定義書 3.1 の表)。 */
@RestController
@Tag(name = ApiTags.AUTH)
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Schema(description = "画面の設定")
    record ConfigResponse(
            @Schema(description = "Google ログインが使えるか。`false` のときは、画面に「Google でログイン」を出さない", example = "true")
            boolean googleEnabled,
            @Schema(description = "お試しログイン(`POST /api/auth/guest`)が使えるか。`false` のときは、画面に「登録せずに試す」を出さない", example = "true")
            boolean guestEnabled) {
    }

    @Schema(description = "ユーザーIDが使えるかの結果")
    record AvailabilityResponse(
            @Schema(description = "使えるか", example = "true") boolean available,
            @Schema(description = "画面にそのまま出せる文。使えるときは「使えます」、使えないときは理由", example = "使えます")
            String message) {
    }

    private final AuthService auth;
    private final UserRepository users;
    private final SessionLogin sessionLogin;
    private final GoogleSettings google;
    private final NotificationService notifications;
    private final GuestService guests;

    AuthController(AuthService auth, UserRepository users, SessionLogin sessionLogin, GoogleSettings google,
            NotificationService notifications, GuestService guests) {
        this.auth = auth;
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.google = google;
        this.notifications = notifications;
        this.guests = guests;
    }

    /** 画面が最初に呼ぶ。Google ログインのボタンを出すかを返す(ついでに CSRF 対策の Cookie も受け取る) */
    @GetMapping("/api/auth/config")
    @SecurityRequirements
    @Operation(summary = "画面の設定の取得",
            description = "Google ログインとお試しログインが使えるかを返す。ログイン不要。画面は最初にこれを呼ぶ。"
                    + "このとき、CSRF 対策の Cookie `XSRF-TOKEN` も受け取れる(データを変える API を呼ぶときに、この値をヘッダー `X-XSRF-TOKEN` に入れて送る)")
    @ApiResponse(responseCode = "200", description = "画面の設定(Google ログインが使えるか)")
    ConfigResponse config() {
        return new ConfigResponse(google.enabled(), guests.enabled());
    }

    @PostMapping("/api/auth/signup")
    @SecurityRequirements
    @Operation(summary = "メールアドレスで登録",
            description = "メールアドレスとパスワードで、「確認前」のユーザーを作り、確認用のリンクをメールで送る"
                    + "(リンクは 24 時間有効で、1 回だけ使える)。メールアドレスは小文字にして保存し、パスワードは BCrypt で変換して保存する。"
                    + "確認前のまま 24 時間たったユーザーは、同じメールアドレスで登録し直せる")
    @ApiResponse(responseCode = "201", description = "登録した。確認メールを送った")
    @ApiResponse(responseCode = "400", description = "入力が正しくない(メールアドレスの形・254 文字以内、パスワードは 8〜72 文字)")
    @ApiResponse(responseCode = "409", description = "登録済みのメールアドレス")
    @ApiResponse(responseCode = "503", description = "メールを送れなかった(しばらくしてからもう一度)")
    ResponseEntity<Void> signup(@Valid @RequestBody AuthRequests.Signup request) {
        auth.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** メールのリンクから。確認できたら、そのままログイン状態にする */
    @PostMapping("/api/auth/verify-email")
    @SecurityRequirements
    @Operation(summary = "メールアドレスの確認",
            description = "確認メールのリンクの `token` を送る。正しければ「確認済み」にして、そのままログイン状態にする(Cookie `SESSION` を返す)。"
                    + "ログインしたユーザーの情報を返す。初期設定前は `onboarded` が `false`")
    @ApiResponse(responseCode = "200", description = "確認できた。ログイン状態になった")
    @ApiResponse(responseCode = "400", description = "リンクが正しくない・期限切れ・すでに使われている")
    MeResponse verifyEmail(@Valid @RequestBody AuthRequests.Token request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = auth.verifyEmail(request.token());
        sessionLogin.login(user, httpRequest, httpResponse);
        return me(user);
    }

    @PostMapping("/api/auth/verify-email/resend")
    @SecurityRequirements
    @Operation(summary = "確認メールの再送",
            description = "確認前のユーザーに、確認メールをもう一度送る。登録の有無にかかわらず同じ応答を返す(登録済みかどうかを知られないため)。"
                    + "同じメールアドレスへは、1 分に 1 回まで")
    @ApiResponse(responseCode = "204", description = "受け付けた(送ったかどうかは教えない)")
    @ApiResponse(responseCode = "429", description = "短い間に繰り返した(1 分に 1 回まで)")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody AuthRequests.EmailOnly request) {
        auth.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    @SecurityRequirements
    @Operation(summary = "ログイン",
            description = "メールアドレスとパスワードでログインし、Cookie `SESSION` を返す(最後の操作から 30 日有効)。"
                    + "失敗のときは、メールアドレスとパスワードのどちらが違うかは教えない。"
                    + "同じメールアドレスで 5 回続けて失敗すると、15 分間ロックする")
    @ApiResponse(responseCode = "200", description = "ログインした。ユーザーの情報を返す")
    @ApiResponse(responseCode = "401", description = "メールアドレスまたはパスワードが違う")
    @ApiResponse(responseCode = "403", description = "メールアドレスの確認が済んでいない、または利用停止中")
    @ApiResponse(responseCode = "423", description = "ログインの失敗が続いたため、15 分間ログインできない")
    MeResponse login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = auth.login(request.email(), request.password());
        sessionLogin.login(user, httpRequest, httpResponse);
        return me(user);
    }

    @PostMapping("/api/auth/guest")
    @SecurityRequirements
    @Operation(summary = "お試しログイン",
            description = "登録せずに、お試し用のアカウント(みんなで使う 1 つのアカウント)でログインし、Cookie `SESSION` を返す。"
                    + "アカウントがなければ作る。お試し用のアカウントは、プロフィールを変えられず、管理者の機能も使えない。"
                    + "書き込み(投稿・いいね・フォローなど)は、毎日 4 時(日本時間)に消える")
    @ApiResponse(responseCode = "200", description = "ログインした。ユーザーの情報を返す(`guest` が `true`)")
    @ApiResponse(responseCode = "404", description = "お試しログインを止めている(設定 `GUEST_LOGIN_ENABLED=false`)")
    MeResponse guest(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = guests.guest();
        sessionLogin.login(user, httpRequest, httpResponse);
        Events.info(log, "auth.login", "ログインしました").addKeyValue("accountId", user.getId())
                .addKeyValue("method", "guest").log();
        return me(user);
    }

    @PostMapping("/api/auth/logout")
    @SecurityRequirements
    @Operation(summary = "ログアウト",
            description = "セッションを消す(リアルタイム配信の接続も切れる)。ログインしていなくても、エラーにはならない")
    @ApiResponse(responseCode = "204", description = "ログアウトした")
    ResponseEntity<Void> logout(@AuthenticationPrincipal LoginUser me, HttpServletRequest httpRequest) {
        if (me != null) {
            Events.info(log, "auth.logout", "ログアウトしました").addKeyValue("accountId", me.id()).log();
        }
        sessionLogin.logout(httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    @Operation(summary = "ログイン中のユーザーの取得",
            description = "ログイン中のユーザーの情報と、未読の通知の数を返す。画面は最初にこれを呼び、401 ならログイン画面へ、"
                    + "初期設定前(`onboarded` が `false`)なら初期設定の画面へ移す")
    @ApiResponse(responseCode = "200", description = "ログイン中のユーザー")
    MeResponse me(@AuthenticationPrincipal LoginUser login) {
        return me(currentUser(login));
    }

    @PostMapping("/api/auth/password-reset")
    @SecurityRequirements
    @Operation(summary = "パスワード再設定メールの送信",
            description = "再設定用のリンクをメールで送る(1 時間有効、1 回だけ使える)。登録の有無にかかわらず同じ応答を返す。"
                    + "Google だけで登録した(パスワードを持たない)ユーザーには送らない")
    @ApiResponse(responseCode = "204", description = "受け付けた(送ったかどうかは教えない)")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody AuthRequests.EmailOnly request) {
        auth.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/password-reset/confirm")
    @SecurityRequirements
    @Operation(summary = "新しいパスワードの設定",
            description = "再設定メールのリンクの `token` と、新しいパスワード(8〜72 文字)を送る。"
                    + "変えると、そのユーザーの、この操作をした端末以外のセッションをすべて消す")
    @ApiResponse(responseCode = "204", description = "パスワードを変えた")
    @ApiResponse(responseCode = "400", description = "リンクが期限切れか、すでに使われている。またはパスワードが 8〜72 文字でない")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthRequests.NewPassword request,
            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        auth.resetPassword(request.token(), request.password(), session == null ? null : session.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/onboarding")
    @Operation(summary = "初期設定(ユーザーIDと表示名)",
            description = "登録(または Google ログイン)の直後に、ユーザーIDと表示名を決める。ユーザーIDは、あとから変えられない。"
                    + "`admin`・`root`・`system` など、運営と見分けがつかなくなる語は使えない。"
                    + "初期設定の前は、この API と `GET /api/auth/me` などの、初期設定に必要な API 以外は 403")
    @ApiResponse(responseCode = "200", description = "初期設定が済んだ。更新後のユーザーを返す")
    @ApiResponse(responseCode = "400", description = "入力が正しくない(ユーザーIDは半角英数字と `_` の 3〜15 文字、表示名は 1〜50 文字、使えない語)、またはすでに初期設定が済んでいる")
    @ApiResponse(responseCode = "409", description = "そのユーザーIDは使われている")
    MeResponse onboarding(@AuthenticationPrincipal LoginUser login,
            @Valid @RequestBody AuthRequests.Onboarding request) {
        return me(auth.completeOnboarding(login.id(), request.handle(), request.displayName()));
    }

    @GetMapping("/api/handles/{handle}/availability")
    @Operation(summary = "ユーザーIDが使えるかの確認",
            description = "入力中のユーザーIDが使えるか(形・使えない語・重複)を確かめる。画面の初期設定で、入力のたびに呼ぶ")
    @ApiResponse(responseCode = "200", description = "使えるか(`available`)と、画面に出せる文(`message`)")
    AvailabilityResponse availability(
            @Parameter(description = "確かめるユーザーID(`@` は付けない)", example = "taro_dev") @PathVariable String handle) {
        String problem = auth.handleProblem(handle);
        return new AvailabilityResponse(problem == null, problem == null ? "使えます" : problem);
    }

    /** ログイン中のユーザーの JSON(未読の通知の数も入れる) */
    private MeResponse me(User user) {
        return MeResponse.from(user, notifications.unreadCount(user.getId()));
    }

    private User currentUser(LoginUser login) {
        return users.findById(login.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください"));
    }
}
