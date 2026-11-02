package com.example.sns.notification;

import com.example.sns.realtime.RealtimeEvents;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 通知(機能定義書 3.7)。作った通知は、データベースへの保存が確定したあとで受け取る人に SSE で届く。
 * ブロック・ミュートしている人からの通知を止める処理(F-NOTIF-06)は M8 で加える。
 */
@Service
@Transactional
public class NotificationService {

    /** 1 つの投稿のメンションの通知は、最初の 10 人まで */
    static final int MAX_MENTIONS = 10;
    private static final int PREVIEW_LENGTH = 40;
    /** メンション: @ の後ろに半角英数字と _ が 3〜15 文字(前の文字が英数字のときは対象外。機能定義書 3.3) */
    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,15})(?![A-Za-z0-9_])");

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationRepository notifications, UserRepository users,
            ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.users = users;
        this.events = events;
    }

    // ---- 作る(F-NOTIF-01, 02) ----

    /** actorId(操作した人)から recipientId(受け取る人)への通知を作る。自分の操作では作らない */
    public void notify(NotificationType type, long actorId, long recipientId, Long postId) {
        if (actorId == recipientId) {
            return;
        }
        // いいね・フォローは、付け直しても増やさない。前の通知を消して作り直し、新しい通知として先頭に出す
        if (type == NotificationType.LIKE || type == NotificationType.FOLLOW) {
            notifications.deleteSame(recipientId, actorId, type, postId);
        }
        Notification saved = notifications.save(new Notification(users.getReferenceById(recipientId),
                users.getReferenceById(actorId), type, postId));
        events.publishEvent(new RealtimeEvents.NotificationCreated(saved.getId(), recipientId));
    }

    /**
     * 本文でメンションされた人に通知する(最初の 10 人まで)。excludeUserIds の人には送らない
     * (返信先の投稿者には「返信」の通知だけを送るため)
     */
    public void notifyMentions(long actorId, long postId, Collection<String> handles, Set<Long> excludeUserIds) {
        List<String> lower = handles.stream().map(User::normalizeHandle).distinct().limit(MAX_MENTIONS).toList();
        if (lower.isEmpty()) {
            return;
        }
        for (User user : users.findByHandleLowerIn(lower)) {
            if (user.isOnboarded() && !user.isSuspended() && !excludeUserIds.contains(user.getId())) {
                notify(NotificationType.MENTION, actorId, user.getId(), postId);
            }
        }
    }

    /** 本文のメンション(@ユーザーID)を、出てきた順に重ならないように取り出す */
    public static Set<String> mentionedHandles(String text) {
        Set<String> handles = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            handles.add(User.normalizeHandle(matcher.group(1)));
        }
        return handles;
    }

    /** 投稿を削除したとき、その投稿についての通知を消す */
    public void deleteForPost(long postId) {
        notifications.deleteByPostId(postId);
    }

    // ---- 一覧と既読(F-NOTIF-03〜05) ----

    @Transactional(readOnly = true)
    public NotificationResponse.Page page(long userId, Long before, Integer limit) {
        int size = limit == null ? 20 : limit;
        if (size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit は1〜50で指定してください");
        }
        List<Notification> found = notifications.findPage(userId, before == null ? Long.MAX_VALUE : before,
                PageRequest.of(0, size + 1));
        boolean hasMore = found.size() > size;
        List<Notification> page = hasMore ? found.subList(0, size) : found;
        return new NotificationResponse.Page(toResponses(page), hasMore ? page.getLast().getId() : null);
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return notifications.countUnread(userId);
    }

    /** upToId 以下の自分の通知をすべて既読にし、残りの未読の数を返す */
    public long markRead(long userId, long upToId) {
        notifications.markRead(userId, upToId, Instant.now());
        return notifications.countUnread(userId);
    }

    /** SSE で送る 1 件分 */
    @Transactional(readOnly = true)
    public NotificationResponse get(long id) {
        return notifications.findWithActor(id).map(n -> toResponses(List.of(n)).getFirst()).orElse(null);
    }

    /** 対象の投稿の本文は、何件あっても 1 回でまとめて取る(N+1 にしないため) */
    private List<NotificationResponse> toResponses(List<Notification> list) {
        List<Long> postIds = list.stream().map(Notification::getPostId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> texts = postIds.isEmpty() ? Map.of() : notifications.findPostTexts(postIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
        return list.stream().map(n -> {
            User actor = n.getActor();
            String avatarUrl = actor.getAvatarPath() == null ? null : "/media/" + actor.getAvatarPath();
            NotificationResponse.PostPreview post = n.getPostId() == null ? null
                    : new NotificationResponse.PostPreview(n.getPostId(), preview(texts.getOrDefault(n.getPostId(), "")));
            return new NotificationResponse(n.getId(), n.getType(),
                    new NotificationResponse.Actor(actor.getId(), actor.getHandle(), actor.getDisplayName(), avatarUrl),
                    post, n.getReadAt() != null, n.getCreatedAt());
        }).toList();
    }

    /** 本文の先頭 40 文字(見た目の 1 文字を 1 文字として数える) */
    private static String preview(String text) {
        if (text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
    }
}
