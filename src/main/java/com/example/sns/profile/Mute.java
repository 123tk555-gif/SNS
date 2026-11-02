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
 * ミュート(機能定義書 4 の mutes テーブル)。muter(ミュートした人)の画面に、muted(された人)の投稿を出さない。
 * 相手には何も知らせない(F-BLOCK-01)。行は MuteRepository.insertIfAbsent で作る。
 */
@Entity
@Table(name = "mutes", uniqueConstraints = @UniqueConstraint(columnNames = {"from_user_id", "to_user_id"}))
@Check(constraints = "from_user_id <> to_user_id")
public class Mute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id")
    private User muter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id")
    private User muted;

    @Column(nullable = false)
    private Instant createdAt;

    protected Mute() {
    }

    User getMuted() {
        return muted;
    }
}
