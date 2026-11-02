package com.example.sns.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /** 複数の投稿の画像を、まとめて 1 回で取る(N+1 にしないため) */
    @Query("""
            select new com.example.sns.post.PostImageRow(i.post.id, i.path, i.alt, i.width, i.height)
            from PostImage i where i.post.id in :postIds order by i.post.id, i.position
            """)
    List<PostImageRow> findRows(@Param("postIds") List<Long> postIds);

    List<PostImage> findByPost(Post post);
}
