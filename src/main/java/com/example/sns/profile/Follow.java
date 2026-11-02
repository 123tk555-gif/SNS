package com.example.sns.profile;

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
import org.hibernate.annotations.Check;

/**
 * フォロー(機能定義書 4 の follows テーブル)。follower(フォローする人)が followee(される人)をフォローしている。
 * 同じ組み合わせは 1 つだけ。自分自身はフォローできない。行は FollowRepository.insertIfAbsent で作る。
 */
@Entity
@Table(name = "follows", uniqueConstraints = @UniqueConstraint(columnNames = {"from_user_id", "to_user_id"}))
@Check(constraints = "from_user_id <> to_user_id")
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id")
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id")
    private User followee;

    @Column(nullable = false)
    private Instant createdAt;

    protected Follow() {
    }

    Long getId() {
        return id;
    }

    User getFollower() {
        return follower;
    }

    User getFollowee() {
        return followee;
    }
}
