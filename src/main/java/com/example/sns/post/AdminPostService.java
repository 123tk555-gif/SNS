package com.example.sns.post;

import com.example.sns.common.LikePatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.sns.realtime.RealtimeEvents;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 管理者による投稿の管理(F-ADMIN-03, 04。機能定義書 3.12)。管理者かどうかは SecurityConfig(/api/admin/**)で確かめ済み。 */
@Service
@Transactional
public class AdminPostService {

    public static final int PAGE_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(AdminPostService.class);

    /** 管理者の画面に出す 1 件分 */
    @Schema(description = "管理者の画面に出す、投稿 1 件分")
    public record AdminPost(
            @Schema(description = "投稿の ID", example = "1234") long id,
            @Schema(description = "投稿者のユーザーの ID", example = "7") long authorId,
            @Schema(description = "投稿者のユーザーID(`@` の後ろ)。初期設定の前は `null`", example = "taro_dev", nullable = true) String authorHandle,
            @Schema(description = "本文", example = "新しいカフェを見つけました #カフェ巡り") String text,
            @Schema(description = "投稿した日時(UTC)", example = "2026-11-01T03:15:00Z") Instant createdAt,
            @Schema(description = "管理者に非表示にされているか", example = "false") boolean hidden,
            @Schema(description = "投稿者が利用停止中か", example = "false") boolean authorSuspended,
            @Schema(description = "返信か", example = "false") boolean reply) {

        static AdminPost from(Post post) {
            return new AdminPost(post.getId(), post.getAuthor().getId(), post.getAuthor().getHandle(), post.getText(),
                    post.getCreatedAt(), post.isHidden(), post.getAuthor().isSuspended(), post.getReplyToId() != null);
        }
    }

    private final PostRepository posts;
    private final ApplicationEventPublisher events;

    public AdminPostService(PostRepository posts, ApplicationEventPublisher events) {
        this.posts = posts;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<AdminPost> search(String q, int page) {
        String word = q == null ? "" : q.strip();
        String pattern = word.isEmpty() ? "" : LikePatterns.contains(word);
        return posts.searchForAdmin(pattern, PageRequest.of(Math.max(page, 0), PAGE_SIZE)).map(AdminPost::from);
    }

    /** 非表示・解除。非表示にしたら、開いている画面から消えるよう post-deleted を配信する */
    public AdminPost setHidden(long adminId, long postId, boolean hidden) {
        Post post = posts.findWithAuthor(postId).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投稿が見つかりません"));
        post.setHidden(hidden);
        if (hidden) {
            events.publishEvent(new RealtimeEvents.PostRemoved(post.getId()));
        }
        log.info("管理者 {} が投稿 {} を{}", adminId, postId, hidden ? "非表示にしました" : "表示に戻しました");
        return AdminPost.from(post);
    }
}
