package com.example.sns.post;

import com.example.sns.user.User;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 投稿を画面に返す形(PostResponse)にする。N+1 にならないよう、何件あっても次をそれぞれ 1 回の SQL でまとめて取る:
 * 画像 / 返信の数 / いいね数といいね済みか / ブックマーク済みか / 返信先の投稿(投稿者つき)。
 * 投稿者は、投稿を取るときに join fetch で一緒に取っておく。
 */
@Component
class PostViews {

    private final PostImageRepository images;
    private final PostRepository posts;
    private final LikeRepository likes;
    private final BookmarkRepository bookmarks;

    PostViews(PostImageRepository images, PostRepository posts, LikeRepository likes, BookmarkRepository bookmarks) {
        this.images = images;
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
    }

    PostResponse toResponse(Post post, long viewerId) {
        return toResponses(List.of(post), viewerId).getFirst();
    }

    List<PostResponse> toResponses(List<Post> list, long viewerId) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Post::getId).toList();
        Map<Long, List<PostResponse.Image>> imagesByPost = images.findRows(ids).stream()
                .collect(Collectors.groupingBy(PostImageRow::postId, Collectors.mapping(
                        row -> new PostResponse.Image("/media/" + row.path(), row.alt(), row.width(), row.height()),
                        Collectors.toList())));
        Map<Long, Long> replyCounts = posts.countReplies(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, Object[]> likeSummary = likes.summarize(ids, viewerId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], Function.identity()));
        Set<Long> bookmarked = new HashSet<>(bookmarks.bookmarkedAmong(viewerId, ids));
        Map<Long, Post> parents = parents(list);

        return list.stream().map(post -> {
            Object[] like = likeSummary.get(post.getId());
            return new PostResponse(
                    post.getId(),
                    author(post.getAuthor()),
                    post.getText(),
                    imagesByPost.getOrDefault(post.getId(), List.of()),
                    replyTo(post, parents),
                    like == null ? 0 : (Long) like[1],
                    replyCounts.getOrDefault(post.getId(), 0L),
                    like != null && ((Number) like[2]).longValue() > 0,
                    bookmarked.contains(post.getId()),
                    post.isWrittenBy(viewerId),
                    post.isHidden(),
                    post.getCreatedAt(),
                    post.getEditedAt(),
                    false);
        }).toList();
    }

    /** 返信先の投稿(返信がなければ空)。返信がいくつあっても 1 回で取る */
    private Map<Long, Post> parents(List<Post> list) {
        List<Long> parentIds = list.stream().map(Post::getReplyToId).filter(Objects::nonNull).distinct().toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return posts.findAllWithAuthor(parentIds).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
    }

    /** 返信先が削除・非表示などで見られないときは unavailable にする(画面は「削除された投稿への返信」と表示) */
    private static PostResponse.ReplyTo replyTo(Post post, Map<Long, Post> parents) {
        if (post.getReplyToId() == null) {
            return null;
        }
        Post parent = parents.get(post.getReplyToId());
        if (parent == null || !parent.isVisible()) {
            return new PostResponse.ReplyTo(post.getReplyToId(), null, true);
        }
        return new PostResponse.ReplyTo(parent.getId(), parent.getAuthor().getHandle(), false);
    }

    private static PostResponse.Author author(User user) {
        String avatarUrl = user.getAvatarPath() == null ? null : "/media/" + user.getAvatarPath();
        return new PostResponse.Author(user.getId(), user.getHandle(), user.getDisplayName(), avatarUrl);
    }
}
