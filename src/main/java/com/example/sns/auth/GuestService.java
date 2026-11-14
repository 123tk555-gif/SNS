package com.example.sns.auth;

import com.example.sns.logging.Events;
import com.example.sns.post.PostService;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * お試しログイン(F-AUTH-08)。登録せずに試せる、みんなで使う 1 つのアカウント。
 * - 最初に使われたときに作る(ユーザーID guest。使われていれば guest_demo)
 * - プロフィールは変えられない(SettingsService)。管理者の機能は使えない(権限は USER)
 * - 書き込み(投稿・いいね・ブックマーク・フォロー・ミュート・ブロック・通知)は、毎日 4 時(日本時間)に消す
 * - 設定 sns.guest.enabled=false(環境変数 GUEST_LOGIN_ENABLED=false)で止められる
 */
@Service
@Transactional
public class GuestService {

    private static final Logger log = LoggerFactory.getLogger(GuestService.class);

    static final String EMAIL = "guest@leafline.invalid";
    static final String DISPLAY_NAME = "お試しユーザー";
    static final String BIO = "登録せずに試せる、お試し用のアカウントです。書き込みは毎日 4 時に消えます";
    public static final String CANNOT_EDIT = "お試しのアカウントでは、プロフィールを変えられません";

    private final UserRepository users;
    private final PostService posts;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    GuestService(UserRepository users, PostService posts, JdbcTemplate jdbc,
            @Value("${sns.guest.enabled:true}") boolean enabled) {
        this.users = users;
        this.posts = posts;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** お試しのアカウントを返す(なければ作る)。止めているときは 404 */
    public User guest() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "お試しログインは使えません");
        }
        return users.findFirstByGuestTrueOrderByIdAsc().orElseGet(() -> {
            String handle = users.existsByHandleLower("guest") ? "guest_demo" : "guest";
            User created = users.save(User.guest(EMAIL, handle, DISPLAY_NAME, BIO));
            Events.info(log, "auth.signup", "お試しのアカウントを作りました").addKeyValue("accountId", created.getId())
                    .addKeyValue("method", "guest").log();
            return created;
        });
    }

    /** お試しのアカウントの書き込みを消す(毎日 4 時。日本時間) */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Tokyo")
    public void cleanup() {
        users.findFirstByGuestTrueOrderByIdAsc().ifPresent(guest -> {
            long id = guest.getId();
            // 投稿は、ふだんの削除と同じ処理で消す(画像のファイル・いいね・通知も消え、開いている画面からも消える)
            List<Long> postIds = jdbc.queryForList(
                    "select id from posts where author_id = ? and deleted_at is null", Long.class, id);
            postIds.forEach(postId -> posts.delete(postId, id));
            int relations = jdbc.update("delete from likes where user_id = ?", id)
                    + jdbc.update("delete from bookmarks where user_id = ?", id)
                    + jdbc.update("delete from follows where from_user_id = ? or to_user_id = ?", id, id)
                    + jdbc.update("delete from mutes where from_user_id = ? or to_user_id = ?", id, id)
                    + jdbc.update("delete from blocks where from_user_id = ? or to_user_id = ?", id, id)
                    + jdbc.update("delete from notifications where actor_id = ? or recipient_id = ?", id, id);
            Events.info(log, "guest.cleanup", "お試しのアカウントの書き込みを消しました").addKeyValue("accountId", id)
                    .addKeyValue("posts", postIds.size()).addKeyValue("others", relations).log();
        });
    }
}
