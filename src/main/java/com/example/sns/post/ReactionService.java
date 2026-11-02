package com.example.sns.post;

import com.example.sns.notification.NotificationService;
import com.example.sns.notification.NotificationType;
import com.example.sns.profile.Relations;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * いいね・ブックマーク(機能定義書 3.4)。どちらも何度送っても同じ結果になる(すでにいいね済みでも、もう一度 PUT してよい)。
 * ブロックの関係にある人の投稿には、いいねできない(403)。ブックマークもできない(投稿自体が見えないので 404)。
 */
@Service
@Transactional
public class ReactionService {

    public record LikeResult(boolean liked, long likeCount) {
    }

    public record BookmarkResult(boolean bookmarked) {
    }

    private final PostRepository posts;
    private final LikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final PostViews views;
    private final NotificationService notifications;
    private final Relations relations;

    public ReactionService(PostRepository posts, LikeRepository likes, BookmarkRepository bookmarks, PostViews views,
            NotificationService notifications, Relations relations) {
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.views = views;
        this.notifications = notifications;
        this.relations = relations;
    }

    // ---- いいね(F-REACT-01〜03) ----

    /** 新しくいいねしたときだけ、投稿者に「いいね」の通知を作る(F-NOTIF-01) */
    public LikeResult like(long userId, long postId) {
        Post post = visiblePost(postId);
        if (relations.blockedEitherWay(userId, post.getAuthor().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, PostService.CANNOT_INTERACT);
        }
        if (likes.insertIfAbsent(userId, postId) == 1) {
            notifications.notify(NotificationType.LIKE, userId, post.getAuthor().getId(), postId);
        }
        return new LikeResult(true, likes.countByPost(postId));
    }

    public LikeResult unlike(long userId, long postId) {
        visiblePost(postId);
        likes.deleteOne(userId, postId);
        return new LikeResult(false, likes.countByPost(postId));
    }

    // ---- ブックマーク(F-REACT-04, 05) ----

    public BookmarkResult bookmark(long userId, long postId) {
        Post post = visiblePost(postId);
        if (relations.blockedEitherWay(userId, post.getAuthor().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, PostService.NOT_FOUND);
        }
        bookmarks.insertIfAbsent(userId, postId);
        return new BookmarkResult(true);
    }

    public BookmarkResult unbookmark(long userId, long postId) {
        visiblePost(postId);
        bookmarks.deleteOne(userId, postId);
        return new BookmarkResult(false);
    }

    /** ブックマークの一覧。ブックマークした新しい順。続きは nextCursor(ブックマークの ID)を before に渡す */
    @Transactional(readOnly = true)
    public PostService.Page bookmarks(long userId, Long before, Integer limit) {
        int size = limit == null ? PostService.DEFAULT_LIMIT : limit;
        if (size < 1 || size > PostService.MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit は1〜50で指定してください");
        }
        List<Bookmark> found = bookmarks.findPage(userId, before == null ? Long.MAX_VALUE : before,
                PageRequest.of(0, size + 1));
        boolean hasMore = found.size() > size;
        List<Bookmark> page = hasMore ? found.subList(0, size) : found;
        List<PostResponse> items = views.toResponses(page.stream().map(Bookmark::getPost).toList(), userId);
        return new PostService.Page(items, hasMore ? page.getLast().getId() : null);
    }

    /** 削除・非表示・投稿者の利用停止に当てはまる投稿は 404(機能定義書 1.6) */
    private Post visiblePost(long postId) {
        return posts.findWithAuthor(postId).filter(Post::isVisible)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PostService.NOT_FOUND));
    }
}
