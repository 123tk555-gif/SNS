package com.example.sns.security;

import com.example.sns.common.ApiError;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン中のユーザーの状態を、リクエストのたびにデータベースで確かめる(機能定義書 2)。
 * - 利用停止中・削除済み: ログイン状態を消し、ログインしていない扱いで先に進める(API なら 401 になる)
 * - 初期設定前: 初期設定に必要な API 以外は 403
 * - 権限(USER / ADMIN)がデータベースで変わっていたら、ログインし直さなくても反映する
 */
class UserStateFilter extends OncePerRequestFilter {

    static final String ONBOARDING_REQUIRED = "初期設定を完了してください";

    /** 初期設定前でも使える API */
    private static final List<String> ALLOWED_BEFORE_ONBOARDING =
            List.of("/api/auth/", "/api/me/onboarding", "/api/handles/");

    private final UserRepository users;
    private final SessionLogin sessionLogin;

    UserStateFilter(UserRepository users, SessionLogin sessionLogin) {
        this.users = users;
        this.sessionLogin = sessionLogin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser login) {
            Optional<User> found = users.findById(login.id());
            if (found.isEmpty() || found.get().isSuspended()) {
                sessionLogin.logout(request);
            } else {
                User user = found.get();
                if (!user.isOnboarded() && needsOnboarding(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(ApiError.json(ONBOARDING_REQUIRED));
                    return;
                }
                if (!authentication.getAuthorities().equals(LoginUser.authorities(user.getRole()))) {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(LoginUser.authentication(user));
                    SecurityContextHolder.setContext(context);
                    sessionLogin.repository().saveContext(context, request, response);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean needsOnboarding(String uri) {
        return uri.startsWith("/api/") && ALLOWED_BEFORE_ONBOARDING.stream().noneMatch(uri::startsWith);
    }
}
