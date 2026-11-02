package com.example.sns.hashtag;

import com.example.sns.post.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 投稿とハッシュタグの対応(機能定義書 4 の post_hashtags テーブル)。
 * created_at には投稿日時を写しておく(トレンドの集計で、投稿の表を見ずに直近 24 時間を絞り込めるように)。
 */
@Entity
@Table(name = "post_hashtags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "hashtag_id"}),
        indexes = @Index(name = "idx_post_hashtags_trend", columnList = "hashtag_id, created_at"))
public class PostHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hashtag_id")
    private Hashtag hashtag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 行は HashtagRepository.link で作る */
    protected PostHashtag() {
    }
}
