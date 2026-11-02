package com.example.sns.post;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostRepository extends JpaRepository<Post, Long> {

    /** 投稿者も一緒に取る(N+1 にしないため) */
    @Query("select p from Post p join fetch p.author where p.id = :id")
    Optional<Post> findWithAuthor(@Param("id") long id);

    /**
     * 「すべて」のタイムライン(F-TL-01, 06, 08): 返信以外で、削除・非表示・投稿者の利用停止に当てはまらない投稿を、
     * before より古いものから新しい順に。投稿者は join fetch で同じ SQL で取る。
     * ブロック・ミュート(V-04, V-05)は M8 で加える。
     */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id < :before
            order by p.id desc
            """)
    List<Post> findTimeline(@Param("before") long before, Pageable pageable);

    /** 投稿ごとの返信の数を、まとめて 1 回で数える(N+1 にしないため) */
    @Query("""
            select p.replyToId, count(p) from Post p
            where p.replyToId in :ids and p.deletedAt is null and p.hidden = false
            group by p.replyToId
            """)
    List<Object[]> countReplies(@Param("ids") List<Long> ids);
}
