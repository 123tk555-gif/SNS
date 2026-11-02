package com.example.sns.realtime;

import com.example.sns.notification.NotificationResponse;
import com.example.sns.notification.NotificationService;
import com.example.sns.post.PostResponse;
import com.example.sns.post.PostService;
import com.example.sns.profile.ProfileService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * データベースへの保存が確定したあとに、出来事を SSE で送る(機能定義書 3.8)。
 * 送る内容を読むために、新しい読み取り専用のトランザクションを使う。
 */
@Component
class RealtimeDispatcher {

    /** post-created の中身。followedByMe: 受け取る人が投稿者をフォローしているか(「フォロー中」に足すかを画面が決める) */
    record PostCreatedPayload(PostResponse post, boolean followedByMe) {
    }

    /** notification の中身。未読の数と、届いた通知 */
    record NotificationPayload(long unreadCount, NotificationResponse notification) {
    }

    private final StreamRegistry registry;
    private final PostService posts;
    private final ProfileService profiles;
    private final NotificationService notifications;

    RealtimeDispatcher(StreamRegistry registry, PostService posts, ProfileService profiles,
            NotificationService notifications) {
        this.registry = registry;
        this.posts = posts;
        this.profiles = profiles;
        this.notifications = notifications;
    }

    /** 返信以外の投稿ができた → 接続中の全員に(投稿者本人の画面には、投稿の API の結果で足すので送らない) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onPostCreated(RealtimeEvents.PostCreated event) {
        PostResponse post = posts.forBroadcast(event.postId());
        if (post == null) {
            return;
        }
        Set<Long> followers = profiles.followerIds(event.authorId());
        registry.broadcast("post-created", userId -> userId == event.authorId()
                ? null : new PostCreatedPayload(post, followers.contains(userId)));
    }

    /** 投稿が削除・非表示にされた → 接続中の全員の画面から消す */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostRemoved(RealtimeEvents.PostRemoved event) {
        registry.broadcast("post-deleted", userId -> Map.of("id", event.postId()));
    }

    /** 通知ができた → 受け取る人に、未読の数と一緒に */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onNotification(RealtimeEvents.NotificationCreated event) {
        if (registry.connections(event.recipientId()) == 0) {
            return;
        }
        NotificationResponse notification = notifications.get(event.notificationId());
        if (notification != null) {
            registry.sendTo(event.recipientId(), "notification",
                    new NotificationPayload(notifications.unreadCount(event.recipientId()), notification));
        }
    }
}
