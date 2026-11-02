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
 * ブロック(機能定義書 4 の blocks テーブル)。blocker(ブロックした人)が blocked(された人)をブロックしている。
 * ブロックの関係にある間は、お互いの投稿が見えず、フォロー・返信・いいねができない(F-BLOCK-02)。
 * 行は BlockRepository.insertIfAbsent で作る。
 */
@Entity
@Table(name = "blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"from_user_id", "to_user_id"}))
@Check(constraints = "from_user_id <> to_user_id")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id")
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id")
    private User blocked;

    @Column(nullable = false)
    private Instant createdAt;

    protected Block() {
    }

    User getBlocked() {
        return blocked;
    }
}
