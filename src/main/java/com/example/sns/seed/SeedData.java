package com.example.sns.seed;

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

    SeedData(UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.update("delete from email_tokens");
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
        String hash = passwordEncoder.encode(PASSWORD);
        for (SeedUser seed : USERS) {
            User user = User.withPassword(seed.email(), hash);
            user.verifyEmail();
            user.completeOnboarding(seed.handle(), seed.name());
            user.setBio(seed.bio());
            if (seed.handle().equals("taro_dev")) {
                user.setRole(Role.ADMIN);
            }
            users.save(user);
        }
        log.info("テストデータを入れました(ユーザー {} 人。パスワードはすべて {})", USERS.length, PASSWORD);
    }
}
