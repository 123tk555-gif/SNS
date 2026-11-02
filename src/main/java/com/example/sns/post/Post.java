package com.example.sns.post;

import com.example.sns.user.User;
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
import java.time.Instant;

/** 投稿(機能定義書 4 の posts テーブル)。削除しても行は残し、「この投稿は削除されました」と表示できるようにする。 */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_author", columnList = "author_id, id"),
        @Index(name = "idx_posts_reply_to", columnList = "reply_to_id, id"),
        @Index(name = "idx_posts_created_at", columnList = "created_at"),
})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    /** 本文(1000 文字まで。絵文字などで文字のデータが長くなっても入る長さにする)。画像だけのときは空文字 */
    @Column(nullable = false, length = 4000)
    private String text;

    /** 返信先の投稿の ID(返信は M4 で作る) */
    @Column(name = "reply_to_id")
    private Long replyToId;

    /** 管理者による非表示 */
    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Instant editedAt;

    private Instant deletedAt;

    protected Post() {
    }

    public Post(User author, String text) {
        this.author = author;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 機能定義書 1.6 の V-01〜V-03(削除・非表示・投稿者の利用停止)に当てはまらない */
    public boolean isVisible() {
        return !isDeleted() && !hidden && !author.isSuspended();
    }

    public boolean isWrittenBy(long userId) {
        return author.getId() == userId;
    }

    void edit(String text) {
        this.text = text;
        this.editedAt = Instant.now();
    }

    /** 本文を空にして削除済みにする(行は残す) */
    void markDeleted() {
        this.text = "";
        this.deletedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
