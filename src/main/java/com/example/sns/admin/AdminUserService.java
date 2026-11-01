package com.example.sns.admin;

import com.example.sns.security.SessionKiller;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 管理者によるユーザーの管理(機能定義書 3.12)。管理者かどうかは SecurityConfig(/api/admin/**)で確かめ済み。 */
@Service
@Transactional
public class AdminUserService {

    static final int PAGE_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository users;
    private final SessionKiller sessionKiller;

    public AdminUserService(UserRepository users, SessionKiller sessionKiller) {
        this.users = users;
        this.sessionKiller = sessionKiller;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, int page) {
        return users.searchForAdmin(q == null ? "" : q.strip(), PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    /** 利用停止・解除。停止したら、そのユーザーのセッションをすべて消す(すぐにログアウトされる) */
    public User setSuspended(long adminId, long userId, boolean suspended) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
        if (user.getId() == adminId || user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身と管理者は利用停止にできません");
        }
        user.setSuspended(suspended);
        if (suspended) {
            sessionKiller.killAll(user.getId(), null);
        }
        log.info("管理者 {} がユーザー {} を{}", adminId, userId, suspended ? "利用停止にしました" : "利用停止から戻しました");
        return user;
    }
}
