package com.example.sns.admin;

import com.example.sns.common.LikePatterns;
import com.example.sns.realtime.StreamRegistry;
import com.example.sns.security.SessionKiller;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final StreamRegistry streams;
    private final JdbcTemplate jdbc;

    public AdminUserService(UserRepository users, SessionKiller sessionKiller, StreamRegistry streams,
            JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.sessionKiller = sessionKiller;
        this.streams = streams;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, int page) {
        String word = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        // % と _ は、ただの文字として探す(LIKE の記号として扱わない)
        String pattern = word.isEmpty() ? "" : LikePatterns.contains(word);
        return users.searchForAdmin(pattern, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    /** ユーザーごとの投稿数(削除済みは数えない)。一覧の 1 ページ分を 1 回の問い合わせで数える(N+1 にしない) */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> postCounts(java.util.List<User> list) {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (list.isEmpty()) {
            return counts;
        }
        String ids = list.stream().map(u -> Long.toString(u.getId())).collect(java.util.stream.Collectors.joining(","));
        jdbc.query("select author_id, count(*) c from posts where deleted_at is null and author_id in (" + ids
                + ") group by author_id", row -> {
            counts.put(row.getLong("author_id"), row.getLong("c"));
        });
        return counts;
    }

    /** 利用停止・解除。停止したら、そのユーザーのセッションと SSE の接続をすべて消す(すぐにログアウトされる) */
    public User setSuspended(long adminId, long userId, boolean suspended) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
        if (user.getId() == adminId || user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身と管理者は利用停止にできません");
        }
        user.setSuspended(suspended);
        if (suspended) {
            sessionKiller.killAll(user.getId(), null);
            streams.disconnect(user.getId());
        }
        log.info("管理者 {} がユーザー {} を{}", adminId, userId, suspended ? "利用停止にしました" : "利用停止から戻しました");
        return user;
    }
}
