package com.example.sns.post;

import com.example.sns.profile.Relations;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 投稿の問い合わせ。一覧はどれも、機能定義書 1.6 の表示ルールで絞り込む:
 * V-01〜V-03(削除・非表示・投稿者の利用停止)と、見る人(viewerId)ごとの V-04・V-05(ブロック・ミュート。Relations)。
 */
interface PostRepository extends JpaRepository<Post, Long> {

    /** 投稿者も一緒に取る(N+1 にしないため) */
    @Query("select p from Post p join fetch p.author where p.id = :id")
    Optional<Post> findWithAuthor(@Param("id") long id);

    /** 「すべて」のタイムライン(F-TL-01, 06〜08): 返信以外を before より古いものから新しい順に。投稿者は同じ SQL で取る */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id < :before and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id desc
            """)
    List<Post> findTimeline(@Param("viewerId") long viewerId, @Param("before") long before, Pageable pageable);

    /** 「フォロー中」のタイムライン(F-TL-02): 「すべて」と同じ条件で、投稿者が自分か、自分がフォローしている人のもの */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id < :before
              and (a.id = :viewerId
                   or a.id in (select f.followee.id from Follow f where f.follower.id = :viewerId))
              and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id desc
            """)
    List<Post> findFollowingTimeline(@Param("viewerId") long viewerId, @Param("before") long before,
            Pageable pageable);

    /** 「すべて」の新着の取り込み(機能定義書 3.5): after より新しい投稿を古い順に */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id asc
            """)
    List<Post> findTimelineAfter(@Param("viewerId") long viewerId, @Param("after") long after, Pageable pageable);

    /** 「フォロー中」の新着の取り込み: after より新しい投稿を古い順に */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId is null and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after
              and (a.id = :viewerId
                   or a.id in (select f.followee.id from Follow f where f.follower.id = :viewerId))
              and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id asc
            """)
    List<Post> findFollowingTimelineAfter(@Param("viewerId") long viewerId, @Param("after") long after,
            Pageable pageable);

    /**
     * ある人の投稿の一覧(プロフィール。F-PROF-03, 04)。新しい順。
     * includeReplies: 返信も含めるか / mediaOnly: 画像付きだけか / includeHidden: 管理者に非表示にされた投稿も含めるか(本人のとき)
     * ミュートした人の投稿は、自分でプロフィールを開いたので表示する(ブロックの関係だけ除く)
     */
    @Query("""
            select p from Post p join fetch p.author a
            where a.id = :authorId and p.deletedAt is null and p.id < :before
              and (p.hidden = false or :includeHidden = true)
              and (p.replyToId is null or :includeReplies = true)
              and (:mediaOnly = false or exists (select 1 from PostImage i where i.post = p))
              and """ + Relations.NOT_BLOCKED + """
            order by p.id desc
            """)
    List<Post> findByAuthor(@Param("viewerId") long viewerId, @Param("authorId") long authorId,
            @Param("includeReplies") boolean includeReplies, @Param("mediaOnly") boolean mediaOnly,
            @Param("includeHidden") boolean includeHidden, @Param("before") long before, Pageable pageable);

    /** ハッシュタグの投稿一覧(F-TREND-03, F-SEARCH-03): そのタグを含む投稿(返信を含む)を新しい順に */
    @Query("""
            select p from Post p join fetch p.author a
            where p.deletedAt is null and p.hidden = false and a.suspended = false and p.id < :before
              and p.id in (select ph.post.id from PostHashtag ph where ph.hashtag.name = :name)
              and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id desc
            """)
    List<Post> findByTag(@Param("viewerId") long viewerId, @Param("name") String name, @Param("before") long before,
            Pageable pageable);

    /**
     * 人気投稿(F-TREND-02): since 以降に作られた返信以外の投稿のうち、いいねが 1 件以上あるものの ID を、
     * いいね数の多い順に(同じ数なら新しい順)
     */
    @Query(value = """
            select p.id from posts p
            join users u on u.id = p.author_id
            join likes l on l.post_id = p.id
            where p.reply_to_id is null and p.deleted_at is null and p.hidden = false and u.suspended = false
              and p.created_at >= :since and """ + Relations.VISIBLE_TO_VIEWER_SQL + """
            group by p.id
            order by count(l.id) desc, p.id desc
            limit :limit
            """, nativeQuery = true)
    List<Long> findPopularIds(@Param("viewerId") long viewerId, @Param("since") Instant since,
            @Param("limit") int limit);

    /** プロフィールに出す投稿数(削除・非表示を除く) */
    @Query("select count(p) from Post p where p.author.id = :authorId and p.deletedAt is null and p.hidden = false")
    long countByAuthor(@Param("authorId") long authorId);

    /** 返信先の投稿を、投稿者と一緒にまとめて 1 回で取る(N+1 にしないため) */
    @Query("select p from Post p join fetch p.author where p.id in :ids")
    List<Post> findAllWithAuthor(@Param("ids") List<Long> ids);

    /**
     * 返信の一覧(F-POST-08): 会話の流れがわかるよう古い順。after より新しいものを取る(カーソル方式)。
     * 削除・非表示・投稿者の利用停止に当てはまる返信と、見る人とブロック・ミュートの関係にある人の返信は出さない
     */
    @Query("""
            select p from Post p join fetch p.author a
            where p.replyToId = :postId and p.deletedAt is null and p.hidden = false and a.suspended = false
              and p.id > :after and """ + Relations.VISIBLE_TO_VIEWER + """
            order by p.id asc
            """)
    List<Post> findReplies(@Param("viewerId") long viewerId, @Param("postId") long postId, @Param("after") long after,
            Pageable pageable);

    /** 投稿ごとの返信の数を、まとめて 1 回で数える(N+1 にしないため) */
    @Query("""
            select p.replyToId, count(p) from Post p
            where p.replyToId in :ids and p.deletedAt is null and p.hidden = false
            group by p.replyToId
            """)
    List<Object[]> countReplies(@Param("ids") List<Long> ids);
}
