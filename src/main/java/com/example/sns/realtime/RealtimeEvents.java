package com.example.sns.realtime;

/**
 * リアルタイムで配信する出来事(機能定義書 3.8)。データを変える処理が ApplicationEventPublisher で発行し、
 * データベースへの保存が確定したあとに RealtimeDispatcher が SSE で送る(保存が取り消されたら送らない)。
 */
public final class RealtimeEvents {

    private RealtimeEvents() {
    }

    /** 返信以外の投稿ができた → 接続中の全員(投稿者本人を除く)に post-created */
    public record PostCreated(long postId, long authorId) {
    }

    /** 投稿が削除・非表示にされた → 接続中の全員に post-deleted */
    public record PostRemoved(long postId) {
    }

    /** 通知ができた → 受け取る人に notification */
    public record NotificationCreated(long notificationId, long recipientId) {
    }
}
