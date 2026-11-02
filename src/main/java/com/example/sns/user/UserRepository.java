package com.example.sns.user;

import java.util.Collection;
import java.util.List;
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

    Optional<User> findByHandleLower(String handleLower);

    List<User> findByHandleLowerIn(Collection<String> handleLowers);

    /**
     * ユーザーの検索(F-SEARCH-02): ユーザーID・表示名の部分一致(全角・半角と大文字・小文字をそろえて比べる)。
     * ユーザーIDが言葉で始まる人を先に、次にフォロワーの多い順。初期設定前・利用停止中の人は出さない
     */
    @Query(value = """
            select u.* from users u
            where u.handle is not null and u.suspended = false
              and (u.handle_lower like :pattern escape '\\'
                   or lower(normalize(u.display_name, NFKC)) like :pattern escape '\\')
            order by (u.handle_lower like :prefix escape '\\') desc,
                     (select count(*) from follows f where f.to_user_id = u.id) desc, u.id
            limit :limit
            """, nativeQuery = true)
    List<User> search(@Param("pattern") String pattern, @Param("prefix") String prefix, @Param("limit") int limit);

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
