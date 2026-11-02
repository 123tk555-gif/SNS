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

    /**
     * 「フォロー中」のタイムライン(F-TL-02): 「すべて」と同じ条件で、投稿者が自分か、自分がフォローしている人のもの
     */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id < :before
              and (a.id = :viewerId
                   or a.id in (select f.followee.id from Follow f where f.follower.id = :viewerId))
            order by p.id desc
            """)
    List<Post> findFollowingTimeline(@Param("viewerId") long viewerId, @Param("before") long before,
            Pageable pageable);

    /** 「すべて」の新着の取り込み(機能定義書 3.5): after より新しい投稿を古い順に */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after
            order by p.id asc
            """)
    List<Post> findTimelineAfter(@Param("after") long after, Pageable pageable);

    /** 「フォロー中」の新着の取り込み: after より新しい投稿を古い順に */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after
              and (a.id = :viewerId
                   or a.id in (select f.followee.id from Follow f where f.follower.id = :viewerId))
            order by p.id asc
            """)
    List<Post> findFollowingTimelineAfter(@Param("viewerId") long viewerId, @Param("after") long after,
            Pageable pageable);

    /**
     * ある人の投稿の一覧(プロフィール。F-PROF-03, 04)。新しい順。
     * includeReplies: 返信も含めるか / mediaOnly: 画像付きだけか / includeHidden: 管理者に非表示にされた投稿も含めるか(本人のとき)
     */
    @Query("""
            select p from Post p join fetch p.author a
            where a.id = :authorId and p.deletedAt is null and p.id < :before
              and (p.hidden = false or :includeHidden = true)
              and (p.replyToId is null or :includeReplies = true)
              and (:mediaOnly = false or exists (select 1 from PostImage i where i.post = p))
            order by p.id desc
            """)
    List<Post> findByAuthor(@Param("authorId") long authorId, @Param("includeReplies") boolean includeReplies,
            @Param("mediaOnly") boolean mediaOnly, @Param("includeHidden") boolean includeHidden,
            @Param("before") long before, Pageable pageable);

    /** プロフィールに出す投稿数(削除・非表示を除く) */
    @Query("select count(p) from Post p where p.author.id = :authorId and p.deletedAt is null and p.hidden = false")
    long countByAuthor(@Param("authorId") long authorId);

    /** 返信先の投稿を、投稿者と一緒にまとめて 1 回で取る(N+1 にしないため) */
    @Query("select p from Post p join fetch p.author where p.id in :ids")
    List<Post> findAllWithAuthor(@Param("ids") List<Long> ids);

    /**
     * 返信の一覧(F-POST-08): 会話の流れがわかるよう古い順。after より新しいものを取る(カーソル方式)。
     * 削除・非表示・投稿者の利用停止に当てはまる返信は出さない
     */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId = :postId and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after
            order by p.id asc
            """)
    List<Post> findReplies(@Param("postId") long postId, @Param("after") long after, Pageable pageable);

    /** 投稿ごとの返信の数を、まとめて 1 回で数える(N+1 にしないため) */
    @Query("""
            select p.replyToId, count(p) from Post p
            where p.replyToId in :ids and p.deletedAt is null and p.hidden = false
            group by p.replyToId
            """)
    List<Object[]> countReplies(@Param("ids") List<Long> ids);
}
