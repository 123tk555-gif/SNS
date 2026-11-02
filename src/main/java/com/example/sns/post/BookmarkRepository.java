package com.example.sns.post;

import com.example.sns.profile.Relations;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** ブックマークする。すでにブックマーク済みなら何もしない(同時に 2 回押されても、エラーにせず 1 件だけ残る) */
    @Modifying
    @Query(value = """
            insert into bookmarks (user_id, post_id, created_at) values (:userId, :postId, now())
            on conflict (user_id, post_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") long userId, @Param("postId") long postId);

    @Modifying
    @Query("delete from Bookmark b where b.user.id = :userId and b.post.id = :postId")
    int deleteOne(@Param("userId") long userId, @Param("postId") long postId);

    @Modifying
    @Query("delete from Bookmark b where b.post = :post")
    void deleteByPost(@Param("post") Post post);

    /** 見る人がブックマークしている投稿の ID を、まとめて 1 回で取る(N+1 にしないため) */
    @Query("select b.post.id from Bookmark b where b.user.id = :viewerId and b.post.id in :postIds")
    List<Long> bookmarkedAmong(@Param("viewerId") long viewerId, @Param("postIds") List<Long> postIds);

    /**
     * ブックマークの一覧(F-REACT-05): ブックマークした新しい順。見られなくなった投稿(ブロック・ミュートを含む)は出さない
     * (ブックマーク自体は残す)。投稿と投稿者は同じ SQL で取る
     */
    @Query("""
            select b from Bookmark b join fetch b.post p join fetch p.author a
            where b.user.id = :viewerId and b.id < :before
              and p.deletedAt is null and p.hidden = false and a.suspended = false
              and """ + Relations.VISIBLE_TO_VIEWER + """
            order by b.id desc
            """)
    List<Bookmark> findPage(@Param("viewerId") long viewerId, @Param("before") long before, Pageable pageable);
}
