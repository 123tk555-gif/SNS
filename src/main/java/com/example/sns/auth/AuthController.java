package com.example.sns.auth;

import com.example.sns.security.GoogleSettings;
import com.example.sns.security.LoginUser;
import com.example.sns.security.SessionLogin;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
class AuthController {

    record ConfigResponse(boolean googleEnabled) {
    }

    record AvailabilityResponse(boolean available, String message) {
    }

    private final AuthService auth;
    private final UserRepository users;
    private final SessionLogin sessionLogin;
    private final GoogleSettings google;

    AuthController(AuthService auth, UserRepository users, SessionLogin sessionLogin, GoogleSettings google) {
        this.auth = auth;
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.google = google;
    }

    /** 画面が最初に呼ぶ。Google ログインのボタンを出すかを返す(ついでに CSRF 対策の Cookie も受け取る) */
    @GetMapping("/api/auth/config")
    ConfigResponse config() {
        return new ConfigResponse(google.enabled());
    }

    @PostMapping("/api/auth/signup")
    ResponseEntity<Void> signup(@Valid @RequestBody AuthRequests.Signup request) {
        auth.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** メールのリンクから。確認できたら、そのままログイン状態にする */
    @PostMapping("/api/auth/verify-email")
    MeResponse verifyEmail(@Valid @RequestBody AuthRequests.Token request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = auth.verifyEmail(request.token());
        sessionLogin.login(user, httpRequest, httpResponse);
        return MeResponse.from(user);
    }

    @PostMapping("/api/auth/verify-email/resend")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody AuthRequests.EmailOnly request) {
        auth.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    MeResponse login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = auth.login(request.email(), request.password());
        sessionLogin.login(user, httpRequest, httpResponse);
        return MeResponse.from(user);
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        sessionLogin.logout(httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    MeResponse me(@AuthenticationPrincipal LoginUser login) {
        return MeResponse.from(currentUser(login));
    }

    @PostMapping("/api/auth/password-reset")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody AuthRequests.EmailOnly request) {
        auth.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/password-reset/confirm")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthRequests.NewPassword request,
            HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        auth.resetPassword(request.token(), request.password(), session == null ? null : session.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/onboarding")
    MeResponse onboarding(@AuthenticationPrincipal LoginUser login,
            @Valid @RequestBody AuthRequests.Onboarding request) {
        return MeResponse.from(auth.completeOnboarding(login.id(), request.handle(), request.displayName()));
    }

    @GetMapping("/api/handles/{handle}/availability")
    AvailabilityResponse availability(@PathVariable String handle) {
        String problem = auth.handleProblem(handle);
        return new AvailabilityResponse(problem == null, problem == null ? "使えます" : problem);
    }

    private User currentUser(LoginUser login) {
        return users.findById(login.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください"));
    }
}
