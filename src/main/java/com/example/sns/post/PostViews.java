package com.example.sns.post;

import com.example.sns.user.User;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 投稿を画面に返す形(PostResponse)にする。N+1 にならないよう、何件あっても
 * 画像・返信の数はそれぞれ 1 回の SQL でまとめて取る(投稿者は投稿と一緒に join fetch で取っておく)。
 */
@Component
class PostViews {

    private final PostImageRepository images;
    private final PostRepository posts;

    PostViews(PostImageRepository images, PostRepository posts) {
        this.images = images;
        this.posts = posts;
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

        // いいね・ブックマークは M4 で作る。それまでは 0 / false
        return list.stream().map(post -> new PostResponse(
                post.getId(),
                author(post.getAuthor()),
                post.getText(),
                imagesByPost.getOrDefault(post.getId(), List.of()),
                null,
                0,
                replyCounts.getOrDefault(post.getId(), 0L),
                false,
                false,
                post.isWrittenBy(viewerId),
                post.isHidden(),
                post.getCreatedAt(),
                post.getEditedAt(),
                false)).toList();
    }

    private static PostResponse.Author author(User user) {
        String avatarUrl = user.getAvatarPath() == null ? null : "/media/" + user.getAvatarPath();
        return new PostResponse.Author(user.getId(), user.getHandle(), user.getDisplayName(), avatarUrl);
    }
}
