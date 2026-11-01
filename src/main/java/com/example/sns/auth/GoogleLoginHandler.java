package com.example.sns.auth;

import com.example.sns.security.SessionLogin;
import com.example.sns.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Google で許可されて戻ってきたときに、ユーザーを決めてログイン状態にする(機能定義書 3.1 の Google ログイン)。
 * Google の情報はここで使い終え、セッションには他のログインと同じ LoginUser だけを残す。
 */
@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);

    private final AuthService auth;
    private final SessionLogin sessionLogin;

    GoogleLoginHandler(AuthService auth, SessionLogin sessionLogin) {
        this.auth = auth;
        this.sessionLogin = sessionLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OidcUser google = (OidcUser) authentication.getPrincipal();
        Optional<User> found = auth.findOrCreateGoogleUser(google.getSubject(), google.getEmail(),
                Boolean.TRUE.equals(google.getEmailVerified()));
        if (found.isEmpty()) {
            log.warn("Google 側でメールアドレスが確認されていないため、既存のユーザーに結びつけませんでした");
            sessionLogin.logout(request);
            response.sendRedirect("/login?error=google");
            return;
        }
        User user = found.get();
        if (user.isSuspended()) {
            sessionLogin.logout(request);
            response.sendRedirect("/login?error=suspended");
            return;
        }
        sessionLogin.login(user, request, response);
        response.sendRedirect(user.isOnboarded() ? "/" : "/onboarding");
    }
}
