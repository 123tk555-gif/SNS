package com.example.sns.seed;

import com.example.sns.hashtag.HashtagService;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 動作確認用のテストデータ。プロファイル seed で起動したときだけ、データを空にしてから入れ直す。
 * 起動: mvnw spring-boot:run "-Dspring-boot.run.profiles=seed"(今までのデータは消える)
 * パスワードはすべて password123。taro_dev は管理者。
 */
@Component
@Profile("seed")
class SeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedData.class);
    private static final String PASSWORD = "password123";

    private record SeedUser(String handle, String name, String email, String bio) {
    }

    private static final SeedUser[] USERS = {
            new SeedUser("taro_dev", "たろう", "taro@example.com", "Java と React を勉強中です。#SpringBoot でアプリを作っています"),
            new SeedUser("hanako", "はなこ", "hanako@example.com", "Web デザインとカフェめぐりが好きです #カフェ巡り"),
            new SeedUser("ken_eng", "けん", "ken@example.com", "インフラエンジニア。AWS と Docker の話をします"),
            new SeedUser("mika", "みか", "mika@example.com", "週末に写真を撮っています #写真"),
            new SeedUser("yuto", "ゆうと", "yuto@example.com", "大学で情報工学を専攻しています"),
    };

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final HashtagService hashtags;

    SeedData(UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc, HashtagService hashtags) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.hashtags = hashtags;
    }

    /** 投稿の本文。上から順に、新しい投稿になる(作者は USERS を順番に回す) */
    private static final String[] POSTS = {
            "新しいカフェを見つけました。窓際の席が気持ちいい #カフェ巡り",
            "Docker Compose で PostgreSQL 18 を立ち上げました。healthcheck を入れておくと起動順で困らなくなります #Docker",
            "今日は #SpringBoot で REST API を作りました。@hanako さん、教えてくれてありがとう!",
            "夕焼けがきれいでした #写真",
            "期末試験が終わりました。これからポートフォリオ作りに集中します",
            "React の useEffect の使い方を復習中",
            "#カフェ巡り 3軒目。チーズケーキが絶品でした",
            "AWS の料金を抑えるコツをまとめています。料金計算ツールが便利です https://calculator.aws/ #AWS",
            "#写真 の練習で、毎朝同じ場所を撮っています",
            "研究室のゼミが終わりました。今日は #React の勉強をします",
    };
    /** テスト投稿の数(無限スクロールを試せるよう、1 ページ 20 件より多くする) */
    private static final int TEST_POSTS = 60;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.update("delete from notifications");
        jdbc.update("delete from blocks");
        jdbc.update("delete from mutes");
        jdbc.update("delete from follows");
        jdbc.update("delete from likes");
        jdbc.update("delete from bookmarks");
        jdbc.update("delete from post_hashtags");
        jdbc.update("delete from post_images");
        jdbc.update("delete from posts");
        jdbc.update("delete from email_tokens");
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        String hash = passwordEncoder.encode(PASSWORD);
        long[] userIds = new long[USERS.length];
        for (int i = 0; i < USERS.length; i++) {
            SeedUser seed = USERS[i];
            User user = User.withPassword(seed.email(), hash);
            user.verifyEmail();
            user.completeOnboarding(seed.handle(), seed.name());
            user.setBio(seed.bio());
            if (seed.handle().equals("taro_dev")) {
                user.setRole(Role.ADMIN);
            }
            userIds[i] = users.save(user).getId();
        }
        users.flush();

        // 古い投稿から順に入れる(ID が大きいほど新しい)。投稿日時は 45 分ずつずらす
        int total = POSTS.length + TEST_POSTS;
        for (int n = total - 1; n >= 0; n--) {
            String text = n < POSTS.length ? POSTS[n] : "テスト投稿です(" + (n - POSTS.length + 1) + "件目)";
            jdbc.update("""
                    insert into posts (author_id, text, hidden, created_at)
                    values (?, ?, false, now() - make_interval(mins => ?))
                    """, userIds[n % userIds.length], text, n * 45 + 3);
        }
        // 新しい 3 件の投稿に返信を付ける(はなこ・けん)
        jdbc.update("""
                insert into posts (author_id, text, reply_to_id, hidden, created_at)
                select u.id, r.text, p.id, false, p.created_at + make_interval(mins => r.minutes)
                from (select id, created_at from posts where reply_to_id is null order by id desc limit 3) p
                cross join (values ('hanako', 'いいですね!', 1), ('ken_eng', '参考になります', 2)) as r(handle, text, minutes)
                join users u on u.handle = r.handle
                """);
        // いいね(投稿と人の組み合わせで、およそ 3 分の 1)と、たろうのブックマーク(新しい 3 件)
        jdbc.update("""
                insert into likes (user_id, post_id, created_at)
                select u.id, p.id, now() from users u cross join posts p
                where p.reply_to_id is null and (p.id + u.id) % 3 = 0
                """);
        jdbc.update("""
                insert into bookmarks (user_id, post_id, created_at)
                select u.id, p.id, now() from users u
                cross join (select id from posts where reply_to_id is null order by id desc limit 3) p
                where u.handle = 'taro_dev'
                """);
        // 投稿は SQL で直接入れたので、ハッシュタグをここで結びつける(トレンド・タグの投稿一覧に出るように)
        jdbc.query("select id, text from posts", (row) -> {
            hashtags.tag(row.getLong("id"), row.getString("text"));
        });
        // フォロー: たろうははなこ・けんを、はなこ・ゆうと・みかはたろうをフォローしている
        jdbc.update("""
                insert into follows (from_user_id, to_user_id, created_at)
                select a.id, b.id, now() from (values
                    ('taro_dev', 'hanako'), ('taro_dev', 'ken_eng'), ('hanako', 'taro_dev'),
                    ('yuto', 'taro_dev'), ('mika', 'taro_dev'), ('hanako', 'mika')) as f(follower, followee)
                join users a on a.handle = f.follower join users b on b.handle = f.followee
                """);
        log.info("テストデータを入れました(ユーザー {} 人・投稿 {} 件・返信・いいね・ブックマーク・フォロー。パスワードはすべて {})",
                USERS.length, total, PASSWORD);
    }
}
