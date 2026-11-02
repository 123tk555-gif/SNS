package com.example.sns.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LikeRepository extends JpaRepository<Like, Long> {

    /** いいねする。すでにいいね済みなら何もしない(同時に 2 回押されても、エラーにせず 1 件だけ残る) */
    @Modifying
    @Query(value = """
            insert into likes (user_id, post_id, created_at) values (:userId, :postId, now())
            on conflict (user_id, post_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") long userId, @Param("postId") long postId);

    @Query("select count(l) from Like l where l.post.id = :postId")
    long countByPost(@Param("postId") long postId);

    @Modifying
    @Query("delete from Like l where l.user.id = :userId and l.post.id = :postId")
    int deleteOne(@Param("userId") long userId, @Param("postId") long postId);

    @Modifying
    @Query("delete from Like l where l.post = :post")
    void deleteByPost(@Param("post") Post post);

    /**
     * 複数の投稿の「いいね数」と「見る人がいいね済みか」を、まとめて 1 回で取る(N+1 にしないため)。
     * 1 行は [投稿の ID, いいね数, 見る人のいいねの数(0 か 1)]。いいねが 0 件の投稿は行がない
     */
    @Query("""
            select l.post.id, count(l), sum(case when l.user.id = :viewerId then 1 else 0 end)
            from Like l where l.post.id in :postIds group by l.post.id
            """)
    List<Object[]> summarize(@Param("postIds") List<Long> postIds, @Param("viewerId") long viewerId);
}
