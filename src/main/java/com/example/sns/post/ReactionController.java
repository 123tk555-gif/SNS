package com.example.sns.post;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** いいね・ブックマークの API(機能定義書 3.4)。 */
@RestController
@Tag(name = ApiTags.REACTION)
class ReactionController {

    private final ReactionService service;

    ReactionController(ReactionService service) {
        this.service = service;
    }

    @PutMapping("/api/posts/{id}/like")
    @Operation(summary = "いいね",
            description = "投稿にいいねする。すでにいいね済みでも、もう一度送ってエラーにしない(何度送っても同じ結果)。"
                    + "投稿者に「いいね」の通知を作る(取り消して付け直しても、通知は増やさない)")
    @ApiResponse(responseCode = "200", description = "いいね済みになった。いいねの数を返す")
    @ApiResponse(responseCode = "403", description = "投稿者との間にブロックの関係がある")
    @ApiResponse(responseCode = "404", description = "見られない投稿(存在しない・削除・非表示・投稿者が利用停止中)")
    ReactionService.LikeResult like(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        return service.like(me.id(), id);
    }

    @DeleteMapping("/api/posts/{id}/like")
    @Operation(summary = "いいねの取り消し",
            description = "いいねを取り消す。いいねしていなくても、エラーにしない(何度送っても同じ結果)")
    @ApiResponse(responseCode = "200", description = "いいねしていない状態になった。いいねの数を返す")
    @ApiResponse(responseCode = "404", description = "見られない投稿")
    ReactionService.LikeResult unlike(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        return service.unlike(me.id(), id);
    }

    @PutMapping("/api/posts/{id}/bookmark")
    @Operation(summary = "ブックマーク",
            description = "投稿をブックマークする(自分だけに見える)。すでに保存済みでも、エラーにしない(何度送っても同じ結果)")
    @ApiResponse(responseCode = "200", description = "ブックマーク済みになった")
    @ApiResponse(responseCode = "404", description = "見られない投稿(ブロックの関係にある人の投稿を含む)")
    ReactionService.BookmarkResult bookmark(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        return service.bookmark(me.id(), id);
    }

    @DeleteMapping("/api/posts/{id}/bookmark")
    @Operation(summary = "ブックマークの解除",
            description = "ブックマークを解除する。保存していなくても、エラーにしない(何度送っても同じ結果)")
    @ApiResponse(responseCode = "200", description = "ブックマークしていない状態になった")
    @ApiResponse(responseCode = "404", description = "見られない投稿")
    ReactionService.BookmarkResult unbookmark(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        return service.unbookmark(me.id(), id);
    }

    /** 自分のブックマークの一覧(他人のブックマークを見る API はない) */
    @GetMapping("/api/bookmarks")
    @Operation(summary = "ブックマークの一覧",
            description = "自分のブックマークを、**ブックマークした新しい順**に返す(他人のブックマークを見る API はない)。"
                    + "見えなくなった投稿(削除・非表示・ブロックなど)は、一覧に出さない(ブックマーク自体は残る)。"
                    + "続きは、前の結果の `nextCursor` を `before` に渡して取る")
    @ApiResponse(responseCode = "200", description = "投稿の一覧。`nextCursor` が `null` なら、それ以上はない")
    PostService.Page bookmarks(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "1234") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return service.bookmarks(me.id(), before, limit);
    }
}
