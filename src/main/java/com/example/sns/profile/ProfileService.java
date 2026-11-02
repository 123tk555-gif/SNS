package com.example.sns.profile;

import com.example.sns.hashtag.HashtagService;
import com.example.sns.hashtag.Hashtags;
import com.example.sns.notification.NotificationService;
import com.example.sns.notification.NotificationType;
import com.example.sns.post.PostService;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * プロフィールとフォロー(機能定義書 3.2・3.6)。
 * ブロックの関係にある相手への制限は M8 で加える。
 */
@Service
@Transactional
public class ProfileService {

    static final String USER_NOT_FOUND = "ユーザーが見つかりません";
    static final String CANNOT_FOLLOW_SELF = "自分自身はフォローできません";

    public record FollowResult(boolean following, long followerCount) {
    }

    private final UserRepository users;
    private final FollowRepository follows;
    private final PostService posts;
    private final NotificationService notifications;

    public ProfileService(UserRepository users, FollowRepository follows, PostService posts,
            NotificationService notifications) {
        this.users = users;
        this.follows = follows;
        this.posts = posts;
        this.notifications = notifications;
    }

    /** ユーザーIDからユーザーを探す。初期設定前・利用停止中(本人以外が見るとき)は見つからない扱い */
    @Transactional(readOnly = true)
    public User findVisible(String handle, long viewerId) {
        return users.findByHandleLower(User.normalizeHandle(handle))
                .filter(User::isOnboarded)
                .filter(user -> !user.isSuspended() || user.getId() == viewerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
    }

    // ---- プロフィール(F-PROF-01, 03) ----

    @Transactional(readOnly = true)
    public ProfileResponse profile(String handle, long viewerId) {
        User user = findVisible(handle, viewerId);
        long id = user.getId();
        boolean me = id == viewerId;
        return new ProfileResponse(id, user.getHandle(), user.getDisplayName(), user.getBio(),
                mediaUrl(user.getAvatarPath()), mediaUrl(user.getHeaderPath()),
                follows.countFollowing(id), follows.countFollowers(id), posts.countByAuthor(id), user.getCreatedAt(),
                !me && !follows.followedBy(viewerId, List.of(id)).isEmpty(),
                !me && !follows.following(viewerId, List.of(id)).isEmpty(),
                false, false, me);
    }

    @Transactional(readOnly = true)
    public PostService.Page posts(String handle, long viewerId, PostService.AuthorTab tab, Long before,
            Integer limit) {
        return posts.byAuthor(findVisible(handle, viewerId).getId(), viewerId, tab, before, limit);
    }

    // ---- フォロー(F-FOLLOW-01, 03) ----

    /** 何度送っても同じ結果。自分自身は 400。新しくフォローしたときだけ、相手に「フォロー」の通知を作る */
    public FollowResult follow(long viewerId, String handle) {
        User target = findVisible(handle, viewerId);
        if (target.getId() == viewerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CANNOT_FOLLOW_SELF);
        }
        if (follows.insertIfAbsent(viewerId, target.getId()) == 1) {
            notifications.notify(NotificationType.FOLLOW, viewerId, target.getId(), null);
        }
        return new FollowResult(true, follows.countFollowers(target.getId()));
    }

    /** その人をフォローしている人の ID(新着の投稿を「フォロー中」に足すかを決めるため。RealtimeDispatcher で使う) */
    @Transactional(readOnly = true)
    public Set<Long> followerIds(long userId) {
        return new HashSet<>(follows.followerIds(userId));
    }

    public FollowResult unfollow(long viewerId, String handle) {
        User target = findVisible(handle, viewerId);
        follows.deleteOne(viewerId, target.getId());
        return new FollowResult(false, follows.countFollowers(target.getId()));
    }

    // ---- ユーザーの検索(F-SEARCH-02) ----

    /** 先頭の「@」は無視する。最大 20 人 */
    @Transactional(readOnly = true)
    public List<UserSummary> search(String query, long viewerId) {
        String word = Hashtags.normalize(query.strip().replaceFirst("^[@＠]", ""));
        if (word.isEmpty()) {
            return List.of();
        }
        String escaped = HashtagService.escapeLike(word);
        return summaries(users.search("%" + escaped + "%", escaped + "%", 20), viewerId);
    }

    // ---- フォロー中・フォロワーの一覧(F-FOLLOW-02) ----

    @Transactional(readOnly = true)
    public UserSummary.Page following(String handle, long viewerId, Long before, Integer limit) {
        User user = findVisible(handle, viewerId);
        int size = pageSize(limit);
        List<Follow> found = follows.findFollowing(user.getId(), cursor(before), PageRequest.of(0, size + 1));
        return page(found, size, viewerId, Follow::getFollowee);
    }

    @Transactional(readOnly = true)
    public UserSummary.Page followers(String handle, long viewerId, Long before, Integer limit) {
        User user = findVisible(handle, viewerId);
        int size = pageSize(limit);
        List<Follow> found = follows.findFollowers(user.getId(), cursor(before), PageRequest.of(0, size + 1));
        return page(found, size, viewerId, Follow::getFollower);
    }

    /** 1 件多く取った結果から、1 ページ分と続きのカーソル(フォローの ID)を作る */
    private UserSummary.Page page(List<Follow> found, int size, long viewerId, Function<Follow, User> person) {
        boolean hasMore = found.size() > size;
        List<Follow> page = hasMore ? found.subList(0, size) : found;
        return new UserSummary.Page(summaries(page.stream().map(person).toList(), viewerId),
                hasMore ? page.getLast().getId() : null);
    }

    /** 一覧用のユーザー。フォローの関係は、何人いても 2 回の SQL でまとめて取る(N+1 にしないため) */
    public List<UserSummary> summaries(List<User> list, long viewerId) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(User::getId).toList();
        Set<Long> followedByMe = new HashSet<>(follows.followedBy(viewerId, ids));
        Set<Long> followsMe = new HashSet<>(follows.following(viewerId, ids));
        return list.stream().map(user -> new UserSummary(user.getId(), user.getHandle(), user.getDisplayName(),
                mediaUrl(user.getAvatarPath()), user.getBio(), followedByMe.contains(user.getId()),
                followsMe.contains(user.getId()))).toList();
    }

    private static int pageSize(Integer limit) {
        int size = limit == null ? 20 : limit;
        if (size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit は1〜50で指定してください");
        }
        return size;
    }

    private static long cursor(Long before) {
        return before == null ? Long.MAX_VALUE : before;
    }

    private static String mediaUrl(String path) {
        return path == null ? null : "/media/" + path;
    }
}
