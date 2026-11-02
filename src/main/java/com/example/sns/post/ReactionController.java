package com.example.sns.post;

import com.example.sns.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** いいね・ブックマークの API(機能定義書 3.4)。 */
@RestController
class ReactionController {

    private final ReactionService service;

    ReactionController(ReactionService service) {
        this.service = service;
    }

    @PutMapping("/api/posts/{id}/like")
    ReactionService.LikeResult like(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        return service.like(me.id(), id);
    }

    @DeleteMapping("/api/posts/{id}/like")
    ReactionService.LikeResult unlike(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        return service.unlike(me.id(), id);
    }

    @PutMapping("/api/posts/{id}/bookmark")
    ReactionService.BookmarkResult bookmark(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        return service.bookmark(me.id(), id);
    }

    @DeleteMapping("/api/posts/{id}/bookmark")
    ReactionService.BookmarkResult unbookmark(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        return service.unbookmark(me.id(), id);
    }

    /** 自分のブックマークの一覧(他人のブックマークを見る API はない) */
    @GetMapping("/api/bookmarks")
    PostService.Page bookmarks(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        return service.bookmarks(me.id(), before, limit);
    }
}
