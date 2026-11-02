package com.example.sns.post;

import com.example.sns.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** ブックマーク(機能定義書 4 の bookmarks テーブル)。本人にしか見えない。 */
@Entity
@Table(name = "bookmarks", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private Instant createdAt;

    /** 行は BookmarkRepository.insertIfAbsent で作る(同時に 2 回押されても安全なように) */
    protected Bookmark() {
    }

    Long getId() {
        return id;
    }

    Post getPost() {
        return post;
    }
}
