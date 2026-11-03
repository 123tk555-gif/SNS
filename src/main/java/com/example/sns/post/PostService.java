package com.example.sns.post;

import com.example.sns.hashtag.HashtagService;
import com.example.sns.hashtag.Hashtags;
import com.example.sns.media.ImageStorage;
import com.example.sns.notification.NotificationService;
import com.example.sns.notification.NotificationType;
import com.example.sns.profile.Relations;
import com.example.sns.realtime.RealtimeEvents;
import com.example.sns.user.UserRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 投稿の作成・取得・編集・削除と、タイムライン(機能定義書 3.3・3.5)。 */
@Service
@Transactional
public class PostService {

    static final int MAX_TEXT = 1000;
    static final int MAX_IMAGES = 10;
    static final int MAX_ALT = 1000;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    static final String EMPTY_POST = "本文か画像のどちらかを入力してください";
    static final String TEXT_TOO_LONG = "1000文字以内で入力してください";
    static final String TOO_MANY_IMAGES = "画像は10枚までです";
    static final String NOT_FOUND = "投稿が見つかりません";
    static final String NOT_MINE = "自分の投稿だけ編集・削除できます";
    public static final String CANNOT_INTERACT = "このユーザーの投稿には、返信・いいねができません";

    /** タイムラインの 1 ページ。nextCursor が null なら、それ以上はない(機能定義書 1.5) */
    @Schema(name = "PostPage", description = "投稿の一覧の 1 ページ")
    public record Page(
            @Schema(description = "投稿(この API の並び順どおり)") List<PostResponse> items,
            @Schema(description = "続きを読み込むときに、`before`(古い順の一覧では `after`)に渡す値。`null` なら、それ以上はない", example = "1214", nullable = true)
            Long nextCursor) {
    }

    private final PostRepository posts;
    private final PostImageRepository images;
    private final LikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final UserRepository users;
    private final ImageStorage storage;
    private final PostViews views;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final HashtagService hashtags;
    private final PostSearch search;
    private final Relations relations;

    public PostService(PostRepository posts, PostImageRepository images, LikeRepository likes,
            BookmarkRepository bookmarks, UserRepository users, ImageStorage storage, PostViews views,
            NotificationService notifications, ApplicationEventPublisher events, HashtagService hashtags,
            PostSearch search, Relations relations) {
        this.relations = relations;
        this.posts = posts;
        this.images = images;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.users = users;
        this.storage = storage;
        this.views = views;
        this.notifications = notifications;
        this.events = events;
        this.hashtags = hashtags;
        this.search = search;
    }

    // ---- 作成・返信(F-POST-01〜03, F-POST-07) ----

    /** replyToId: 返信先の投稿の ID(返信でなければ null)。返信先が見られない投稿なら 404 */
    public PostResponse create(long authorId, String text, List<MultipartFile> files, List<String> alts,
            Long replyToId) {
        String body = normalize(text);
        List<MultipartFile> imageFiles = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        requireValidText(body, !imageFiles.isEmpty());
        if (imageFiles.size() > MAX_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TOO_MANY_IMAGES);
        }
        List<String> altTexts = alts == null ? List.of() : alts;
        for (String alt : altTexts) {
            if (alt != null && alt.strip().codePointCount(0, alt.strip().length()) > MAX_ALT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "画像の説明は1000文字以内で入力してください");
            }
        }

        Post parent = replyToId == null ? null : posts.findWithAuthor(replyToId).filter(Post::isVisible)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "返信先の投稿が見つかりません"));
        // ブロックの関係にある人の投稿には返信できない(F-BLOCK-02)
        if (parent != null && relations.blockedEitherWay(authorId, parent.getAuthor().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, CANNOT_INTERACT);
        }

        Post post = posts.save(new Post(users.getReferenceById(authorId), body, replyToId));

        // 画像は uploads/posts/年/月/ に保存する。途中で失敗して取り消しになったら、保存したファイルも消す
        String folder = "posts/" + YearMonth.now(ZoneOffset.UTC).toString().replace('-', '/');
        List<String> saved = new ArrayList<>();
        storage.deleteOnRollback(saved);
        for (int i = 0; i < imageFiles.size(); i++) {
            ImageStorage.StoredImage stored = storage.store(imageFiles.get(i), folder);
            saved.add(stored.path());
            String alt = i < altTexts.size() && altTexts.get(i) != null ? altTexts.get(i).strip() : "";
            images.save(new PostImage(post, stored.path(), alt, stored.width(), stored.height(), i));
        }
        posts.flush();
        hashtags.tag(post.getId(), body);

        // 通知(F-NOTIF-01): 返信なら返信先の投稿者へ「返信」、本文のメンション相手へ「メンション」。
        // 返信先の投稿者がメンションもされていたら、「返信」の通知だけを送る
        Set<Long> repliedTo = parent == null ? Set.of() : Set.of(parent.getAuthor().getId());
        if (parent != null) {
            notifications.notify(NotificationType.REPLY, authorId, parent.getAuthor().getId(), post.getId());
        }
        notifications.notifyMentions(authorId, post.getId(), NotificationService.mentionedHandles(body), repliedTo);
        // 返信以外は、接続中の人のタイムラインにすぐに届ける(F-TL-04。保存が確定したあとに送る)
        if (parent == null) {
            events.publishEvent(new RealtimeEvents.PostCreated(post.getId(), authorId));
        }
        return views.toResponse(post, authorId);
    }

    // ---- 取得(F-POST-08, 09) ----

    /**
     * 投稿 1 件。削除・非表示・投稿者の利用停止のときは「表示できない投稿」として返し、
     * 投稿者とブロックの関係にあるときは 404(機能定義書 1.6)。ミュートした人の投稿は、自分で開いたので表示する
     */
    @Transactional(readOnly = true)
    public PostResponse get(long id, long viewerId) {
        Post post = posts.findWithAuthor(id)
                .filter(p -> !relations.blockedEitherWay(viewerId, p.getAuthor().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        boolean ownHidden = post.isHidden() && !post.isDeleted() && post.isWrittenBy(viewerId);
        if (!post.isVisible() && !ownHidden) {
            return PostResponse.unavailable(id);
        }
        return views.toResponse(post, viewerId);
    }

    // ---- 編集(F-POST-05) ----

    /** 編集で新しく増えたメンションの相手にだけ、通知を作る(機能定義書 3.3) */
    public PostResponse edit(long id, long viewerId, String text) {
        Post post = findOwnPost(id, viewerId);
        String body = normalize(text);
        requireValidText(body, !images.findByPost(post).isEmpty());
        Set<String> added = new LinkedHashSet<>(NotificationService.mentionedHandles(body));
        added.removeAll(NotificationService.mentionedHandles(post.getText()));
        post.edit(body);
        posts.flush();
        hashtags.tag(post.getId(), body);
        Set<Long> repliedTo = post.getReplyToId() == null ? Set.of()
                : posts.findWithAuthor(post.getReplyToId()).map(p -> Set.of(p.getAuthor().getId())).orElse(Set.of());
        notifications.notifyMentions(viewerId, post.getId(), added, repliedTo);
        return views.toResponse(post, viewerId);
    }

    // ---- 削除(F-POST-06) ----

    /**
     * 行は残して削除済みにし、本文を空にする。画像は行もファイルも消す。いいね・ブックマーク・通知も消す。
     * 開いている画面からも消えるよう、接続中の全員に知らせる
     */
    public void delete(long id, long viewerId) {
        Post post = findOwnPost(id, viewerId);
        likes.deleteByPost(post);
        bookmarks.deleteByPost(post);
        notifications.deleteForPost(post.getId());
        hashtags.untag(post.getId());
        List<PostImage> postImages = images.findByPost(post);
        images.deleteAll(postImages);
        storage.deleteAfterCommit(postImages.stream().map(PostImage::getPath).toList());
        post.markDeleted();
        events.publishEvent(new RealtimeEvents.PostRemoved(post.getId()));
    }

    // ---- タイムライン(F-TL-01, 03) ----

    /** following: true なら「フォロー中」(自分とフォローしている人の投稿)、false なら「すべて」 */
    @Transactional(readOnly = true)
    public Page timeline(long viewerId, boolean following, Long before, Integer limit) {
        int size = pageSize(limit);
        long cursor = before == null ? Long.MAX_VALUE : before;
        // 1 件多く取り、続きがあるかを確かめる
        PageRequest request = PageRequest.of(0, size + 1);
        List<Post> found = following
                ? posts.findFollowingTimeline(viewerId, cursor, request)
                : posts.findTimeline(viewerId, cursor, request);
        return page(found, size, viewerId);
    }

    /** 新着の取り込みで一度に返す数(機能定義書 3.5) */
    static final int CATCH_UP_LIMIT = 50;

    /**
     * 新着の取り込み(F-TL-04): SSE がつなぎ直したときに、その間に増えた投稿(after より新しいもの)を
     * 古い順に最大 50 件返す。それより多いときは nextCursor が null 以外になる(画面はタイムラインを読み込み直す)
     */
    @Transactional(readOnly = true)
    public Page timelineAfter(long viewerId, boolean following, long after) {
        PageRequest request = PageRequest.of(0, CATCH_UP_LIMIT + 1);
        List<Post> found = following
                ? posts.findFollowingTimelineAfter(viewerId, after, request)
                : posts.findTimelineAfter(viewerId, after, request);
        return page(found, CATCH_UP_LIMIT, viewerId);
    }

    /** SSE で配信する投稿(見る人ごとの「いいね済み」などは、新しい投稿なのでまだない) */
    @Transactional(readOnly = true)
    public PostResponse forBroadcast(long postId) {
        return posts.findWithAuthor(postId).filter(Post::isVisible).map(post -> views.toResponse(post, -1)).orElse(null);
    }

    // ---- ある人の投稿の一覧(プロフィール。F-PROF-03, 04) ----

    public enum AuthorTab {
        /** 返信以外 */
        POSTS,
        /** 返信を含むすべて */
        REPLIES,
        /** 画像付き(返信を含む) */
        MEDIA
    }

    /** 本人が見るときは、管理者に非表示にされた投稿も(印を付けて)出す(機能定義書 1.6 の例外) */
    @Transactional(readOnly = true)
    public Page byAuthor(long authorId, long viewerId, AuthorTab tab, Long before, Integer limit) {
        int size = pageSize(limit);
        List<Post> found = posts.findByAuthor(viewerId, authorId, tab != AuthorTab.POSTS, tab == AuthorTab.MEDIA,
                authorId == viewerId, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, size + 1));
        return page(found, size, viewerId);
    }

    @Transactional(readOnly = true)
    public long countByAuthor(long authorId) {
        return posts.countByAuthor(authorId);
    }

    // ---- ハッシュタグ・トレンド・検索(F-TREND-02, 03, F-SEARCH-01, 03) ----

    /** ハッシュタグを含む投稿(返信を含む)。新しい順 */
    @Transactional(readOnly = true)
    public Page byTag(String name, long viewerId, Long before, Integer limit) {
        int size = pageSize(limit);
        List<Post> found = posts.findByTag(viewerId, Hashtags.normalize(name.replaceFirst("^[#＃]", "")),
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, size + 1));
        return page(found, size, viewerId);
    }

    /** 人気投稿: 直近 24 時間に作られた返信以外の投稿のうち、いいね数の多い順に上位 limit 件 */
    @Transactional(readOnly = true)
    public List<PostResponse> popular(long viewerId, int limit) {
        return inOrder(posts.findPopularIds(viewerId, Instant.now().minus(HashtagService.TREND_PERIOD), limit), viewerId);
    }

    /** 本文の検索。空白(全角・半角)で区切った言葉を、すべて含む投稿(返信を含む)を新しい順に */
    @Transactional(readOnly = true)
    public Page search(List<String> words, long viewerId, Long before, Integer limit) {
        int size = pageSize(limit);
        List<Long> ids = search.findIds(viewerId, words, before == null ? Long.MAX_VALUE : before, size + 1);
        boolean hasMore = ids.size() > size;
        List<Long> pageIds = hasMore ? ids.subList(0, size) : ids;
        return new Page(inOrder(pageIds, viewerId), hasMore ? pageIds.getLast() : null);
    }

    /** ID の順番どおりに、投稿を画面に返す形にする(投稿者つきで 1 回で取り、あとはまとめて取る) */
    private List<PostResponse> inOrder(List<Long> ids, long viewerId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> found = posts.findAllWithAuthor(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> ordered = ids.stream().map(found::get).filter(Objects::nonNull).toList();
        return views.toResponses(ordered, viewerId);
    }

    // ---- 返信の一覧(F-POST-08) ----

    /** 古い順に最大 limit 件。続きは nextCursor を after に渡して取る。返信先が削除済みでも返信は表示する */
    @Transactional(readOnly = true)
    public Page replies(long postId, long viewerId, Long after, Integer limit) {
        if (!posts.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        int size = pageSize(limit);
        List<Post> found = posts.findReplies(viewerId, postId, after == null ? 0 : after, PageRequest.of(0, size + 1));
        return page(found, size, viewerId);
    }

    private int pageSize(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit は1〜50で指定してください");
        }
        return size;
    }

    /** 1 件多く取った結果から、1 ページ分と続きのカーソルを作る */
    private Page page(List<Post> found, int size, long viewerId) {
        boolean hasMore = found.size() > size;
        List<Post> page = hasMore ? found.subList(0, size) : found;
        return new Page(views.toResponses(page, viewerId), hasMore ? page.getLast().getId() : null);
    }

    private Post findOwnPost(long id, long viewerId) {
        Post post = posts.findWithAuthor(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!post.isWrittenBy(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_MINE);
        }
        return post;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip();
    }

    /** 文字数は、見た目の 1 文字を 1 文字として数える(絵文字も 1 文字。機能定義書 1.1) */
    private static void requireValidText(String body, boolean hasImages) {
        if (body.isEmpty() && !hasImages) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EMPTY_POST);
        }
        if (body.codePointCount(0, body.length()) > MAX_TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TEXT_TOO_LONG);
        }
    }
}
