package com.example.sns.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** email は小文字にしたもので探す(User.normalizeEmail) */
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    /** handleLower は小文字にしたユーザーID(User.normalizeHandle) */
    boolean existsByHandleLower(String handleLower);

    /** 管理者画面のユーザー一覧: ユーザーID・表示名・メールアドレスの部分一致。登録の新しい順 */
    @Query("""
            select u from User u
            where :q = ''
               or lower(u.handle) like lower(concat('%', :q, '%'))
               or lower(u.displayName) like lower(concat('%', :q, '%'))
               or u.email like lower(concat('%', :q, '%'))
            order by u.createdAt desc, u.id desc
            """)
    Page<User> searchForAdmin(@Param("q") String q, Pageable pageable);
}
