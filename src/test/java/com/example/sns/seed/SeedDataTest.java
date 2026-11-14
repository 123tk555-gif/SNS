package com.example.sns.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.ApiTestSupport;
import com.example.sns.hashtag.HashtagService;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** テストデータ(プロファイル seed)を入れ直しても、ふだん使いのアカウントは消えない */
class SeedDataTest extends ApiTestSupport {

    @Autowired
    HashtagService hashtags;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 入れ直しても_Googleで作ったアカウントは残り_テスト用のユーザーだけが入れ替わる() {
        User google = User.withGoogle("me@gmail.com", "google-sub-1");
        google.completeOnboarding("myself", "わたし");
        google.setRole(Role.ADMIN);
        long googleId = users.save(google).getId();
        createUser("e2e_tmp", Role.USER);

        SeedData seed = new SeedData(users, passwordEncoder, jdbc, hashtags);
        seed.run(null);
        seed.run(null); // サーバーを起動し直したときと同じ

        User kept = users.findById(googleId).orElseThrow();
        assertThat(kept.getRole()).isEqualTo(Role.ADMIN);
        assertThat(kept.getHandle()).isEqualTo("myself");
        assertThat(count("select count(*) from users where handle like 'e2e\\_%'")).isZero();
        // テストデータの 5 人は、1 人ずつだけ(二重にならない)
        assertThat(count("select count(*) from users where email like '%@example.com'")).isEqualTo(5);
        assertThat(count("select count(*) from users where handle = 'taro_dev' and role = 'ADMIN'")).isEqualTo(1);
        assertThat(count("select count(*) from posts where reply_to_id is null")).isEqualTo(70);
        // ふだん使いのアカウントに、テストデータのいいね・フォローは付かない
        assertThat(count("select count(*) from likes where user_id = ?", googleId)).isZero();
        assertThat(count("select count(*) from follows where from_user_id = ? or to_user_id = ?", googleId, googleId))
                .isZero();
    }
}
