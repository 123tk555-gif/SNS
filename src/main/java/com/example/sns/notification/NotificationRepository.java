package com.example.sns.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 一覧に出してよい通知の条件(機能定義書 3.7): 操作した人が利用停止中でなく、
     * 対象の投稿があれば、それが削除・非表示になっていないこと。
     * あとからブロック・ミュートした人の通知も出さない(F-NOTIF-06)
     */
    String VISIBLE = """
            n.recipient.id = :userId and n.actor.suspended = false
              and (n.postId is null or exists (
                   select 1 from Post p where p.id = n.postId and p.deletedAt is null and p.hidden = false))
              and not exists (select 1 from Block blk
                              where (blk.blocker.id = :userId and blk.blocked.id = n.actor.id)
                                 or (blk.blocker.id = n.actor.id and blk.blocked.id = :userId))
              and not exists (select 1 from Mute mt where mt.muter.id = :userId and mt.muted.id = n.actor.id)
            """;

    /** 通知の一覧(新しい順)。操作した人は同じ SQL で取る */
    @Query("select n from Notification n join fetch n.actor where " + VISIBLE + " and n.id < :before order by n.id desc")
    List<Notification> findPage(@Param("userId") long userId, @Param("before") long before, Pageable pageable);

    @Query("select n from Notification n join fetch n.actor where n.id = :id")
    Optional<Notification> findWithActor(@Param("id") long id);

    @Query("select count(n) from Notification n where " + VISIBLE + " and n.readAt is null")
    long countUnread(@Param("userId") long userId);

    /** upToId 以下の自分の通知を、すべて既読にする */
    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipient.id = :userId and n.id <= :upToId and n.readAt is null
            """)
    int markRead(@Param("userId") long userId, @Param("upToId") long upToId, @Param("now") Instant now);

    /** いいね・フォローの通知を作り直すとき、前の同じ通知を消す(同じ通知を 2 つ作らない) */
    @Modifying
    @Query("""
            delete from Notification n
            where n.recipient.id = :recipientId and n.actor.id = :actorId and n.type = :type
              and ((:postId is null and n.postId is null) or n.postId = :postId)
            """)
    void deleteSame(@Param("recipientId") long recipientId, @Param("actorId") long actorId,
            @Param("type") NotificationType type, @Param("postId") Long postId);

    /** 投稿を削除したとき、その投稿についての通知を消す */
    @Modifying
    @Query("delete from Notification n where n.postId = :postId")
    void deleteByPostId(@Param("postId") long postId);

    /** 通知の一覧に出す、対象の投稿の本文(まとめて 1 回で取る) */
    @Query("select p.id, p.text from Post p where p.id in :ids")
    List<Object[]> findPostTexts(@Param("ids") List<Long> ids);
}
