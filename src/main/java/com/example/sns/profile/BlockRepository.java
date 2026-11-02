package com.example.sns.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlockRepository extends JpaRepository<Block, Long> {

    /** ブロックする。すでにブロック済みなら何もしない。登録した件数(0 か 1)を返す */
    @Modifying
    @Query(value = """
            insert into blocks (from_user_id, to_user_id, created_at) values (:blockerId, :blockedId, now())
            on conflict (from_user_id, to_user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("blockerId") long blockerId, @Param("blockedId") long blockedId);

    @Modifying
    @Query("delete from Block b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    int deleteOne(@Param("blockerId") long blockerId, @Param("blockedId") long blockedId);

    /** a と b の間に、どちら向きでもブロックがあるか */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)
            """)
    boolean existsEitherWay(@Param("a") long a, @Param("b") long b);

    @Query("select count(b) > 0 from Block b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    boolean exists(@Param("blockerId") long blockerId, @Param("blockedId") long blockedId);

    /** userId とブロックの関係にある人(userId がブロックした人と、userId をブロックした人) */
    @Query("""
            select case when b.blocker.id = :userId then b.blocked.id else b.blocker.id end
            from Block b where b.blocker.id = :userId or b.blocked.id = :userId
            """)
    List<Long> relatedIds(@Param("userId") long userId);

    /** ブロックした人の一覧(ブロックした新しい順)。相手のユーザーは同じ SQL で取る */
    @Query("select b from Block b join fetch b.blocked where b.blocker.id = :userId order by b.id desc")
    List<Block> findByBlocker(@Param("userId") long userId);
}
