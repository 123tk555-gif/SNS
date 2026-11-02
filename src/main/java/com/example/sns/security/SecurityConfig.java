package com.example.sns.security;

import com.example.sns.auth.GoogleLoginHandler;
import com.example.sns.common.ApiError;
import com.example.sns.user.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン・権限・CSRF 対策の設定(要件定義書 NF-SEC、機能定義書 1.2・2)。
 * URL ごとの許可はここで決め、「本人のデータか」などデータごとの確認は各機能の Service で行う。
 */
@Configuration
public class SecurityConfig {

    static final String LOGIN_REQUIRED = "ログインしてください";
    static final String CSRF_FAILED = "画面を読み込み直してから、もう一度お試しください";
    static final String FORBIDDEN = "この操作の権限がありません";

    /** 画面の部品の読み込み元の制限(Content-Security-Policy) */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; "
            + "img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessionLogin sessionLogin, UserRepository users,
            GoogleSettings google, GoogleLoginHandler googleLoginHandler) throws Exception {
        http
                // CSRF 対策: Cookie XSRF-TOKEN の値を、画面がヘッダー X-XSRF-TOKEN に入れて送る
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // ブラウザに「この画面は自分のサイトの部品だけを読み込む」と伝える(XSS が起きても外へ送れないようにする)。
                // 外から読むのは、文字のフォント(Google Fonts)だけ。画像は自分のサイトと、投稿前のプレビュー(blob:)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
                .securityContext(context -> context.securityContextRepository(sessionLogin.repository()))
                // ログインは自前の API(/api/auth/login)で行うので、標準のログイン画面などは使わない
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // SSE(/api/stream)が終わるときなどの内部の処理の続き。最初のリクエストで確かめ済み
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/signup", "/api/auth/login", "/api/auth/logout",
                                "/api/auth/verify-email", "/api/auth/verify-email/resend",
                                "/api/auth/password-reset", "/api/auth/password-reset/confirm")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/config").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**", "/media/**").authenticated()
                        // 画面のファイル(index.html・JS・CSS)は誰でも取れる。どの画面を出すかは画面側が決める
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, LOGIN_REQUIRED))
                        .accessDeniedHandler((request, response, e) -> {
                            if (e instanceof CsrfException) {
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, CSRF_FAILED);
                            } else if (request.getRequestURI().startsWith("/api/admin/")) {
                                // 管理者の機能があることを知られないよう、存在しない URL と同じ 404 にする
                                writeError(response, HttpServletResponse.SC_NOT_FOUND, ApiError.NOT_FOUND_MESSAGE);
                            } else {
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN);
                            }
                        }))
                .addFilterBefore(new UserStateFilter(users, sessionLogin), AnonymousAuthenticationFilter.class);

        if (google.enabled()) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(google.registrations())
                    .loginPage("/login")
                    .successHandler(googleLoginHandler)
                    .failureHandler((request, response, e) -> response.sendRedirect("/login?error=google")));
        }
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(ApiError.json(message));
    }

    /** CSRF 対策の値を毎回読み出して、Cookie XSRF-TOKEN が必ず画面に届くようにする */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
