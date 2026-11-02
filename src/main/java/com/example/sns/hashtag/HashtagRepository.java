package com.example.sns.hashtag;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    /** タグ名を登録する(すでにあれば何もしない) */
    @Modifying
    @Query(value = "insert into hashtags (name) values (:name) on conflict (name) do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);

    /** 投稿とタグを結びつける。created_at には投稿日時を写す */
    @Modifying
    @Query(value = """
            insert into post_hashtags (post_id, hashtag_id, created_at)
            select p.id, h.id, p.created_at from posts p, hashtags h where p.id = :postId and h.name = :name
            on conflict (post_id, hashtag_id) do nothing
            """, nativeQuery = true)
    void link(@Param("postId") long postId, @Param("name") String name);

    @Modifying
    @Query(value = "delete from post_hashtags where post_id = :postId", nativeQuery = true)
    void unlinkAll(@Param("postId") long postId);

    /**
     * トレンドのハッシュタグ(F-TREND-01): since 以降に使われたタグを、使われた投稿の数の多い順に。
     * 同じ数なら、最後に使われたのが新しい順。削除・非表示の投稿と、利用停止中のユーザーの投稿は数えない。
     * 1 行は [タグ名, 投稿の数]
     */
    @Query(value = """
            select h.name, count(*) as post_count
            from post_hashtags ph
            join hashtags h on h.id = ph.hashtag_id
            join posts p on p.id = ph.post_id
            join users u on u.id = p.author_id
            where ph.created_at >= :since and p.deleted_at is null and p.hidden = false and u.suspended = false
            group by h.name
            order by post_count desc, max(ph.created_at) desc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> trending(@Param("since") Instant since, @Param("limit") int limit);

    /**
     * ハッシュタグの検索(F-SEARCH-03): 名前に word を含むタグを、名前が word で始まるものを先に、
     * 次に投稿の数の多い順に。1 行は [タグ名, 投稿の数]
     */
    @Query(value = """
            select h.name, count(p.id) as post_count
            from hashtags h
            join post_hashtags ph on ph.hashtag_id = h.id
            join posts p on p.id = ph.post_id and p.deleted_at is null and p.hidden = false
            join users u on u.id = p.author_id and u.suspended = false
            where h.name like :pattern escape '\\'
            group by h.name
            order by (h.name like :prefix escape '\\') desc, post_count desc, h.name
            limit :limit
            """, nativeQuery = true)
    List<Object[]> search(@Param("pattern") String pattern, @Param("prefix") String prefix, @Param("limit") int limit);
}
