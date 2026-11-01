package com.example.sns.security;

import com.example.sns.user.Role;
import com.example.sns.user.User;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * セッションに入れる「ログイン中のユーザー」。中身はユーザーの ID だけにし、毎回データベースから読み直す
 * (UserStateFilter)。名前(getName)はユーザーの ID で、Spring Session がユーザーごとにセッションを探すときに使う。
 */
public record LoginUser(long id) implements Principal, Serializable {

    @Override
    public String getName() {
        return Long.toString(id);
    }

    public static Authentication authentication(User user) {
        return authentication(user.getId(), user.getRole());
    }

    static Authentication authentication(long userId, Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, authorities(role));
    }

    static List<GrantedAuthority> authorities(Role role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
