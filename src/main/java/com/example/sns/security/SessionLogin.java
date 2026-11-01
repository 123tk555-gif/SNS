package com.example.sns.security;

import com.example.sns.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** ログイン状態をセッションに保存する・消す。セッションはデータベースに保存される(Spring Session JDBC)。 */
@Component
public class SessionLogin {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    SecurityContextRepository repository() {
        return repository;
    }

    public void login(User user, HttpServletRequest request, HttpServletResponse response) {
        // ログインの前後でセッションの ID を変える(ログイン前の ID を知られていても乗っ取れないように)
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(LoginUser.authentication(user));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
