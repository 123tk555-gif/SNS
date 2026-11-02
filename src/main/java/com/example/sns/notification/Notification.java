package com.example.sns.notification;

import com.example.sns.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 通知(機能定義書 4 の notifications テーブル)。 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_recipient", columnList = "recipient_id, id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationType type;

    /** 対象の投稿(フォローのときは null) */
    @Column(name = "post_id")
    private Long postId;

    /** 既読にした日時。未読のときは null */
    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    Notification(User recipient, User actor, NotificationType type, Long postId) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.postId = postId;
        this.createdAt = Instant.now();
    }

    Long getId() {
        return id;
    }

    User getRecipient() {
        return recipient;
    }

    User getActor() {
        return actor;
    }

    NotificationType getType() {
        return type;
    }

    Long getPostId() {
        return postId;
    }

    Instant getReadAt() {
        return readAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
