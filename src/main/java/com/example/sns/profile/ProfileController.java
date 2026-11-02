package com.example.sns.profile;

import com.example.sns.post.PostService;
import com.example.sns.security.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** プロフィール・フォローの API(機能定義書 3.2・3.6)。 */
@RestController
class ProfileController {

    private final ProfileService service;

    ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/users/{handle}")
    ProfileResponse profile(@AuthenticationPrincipal LoginUser me, @PathVariable String handle) {
        return service.profile(handle, me.id());
    }

    /** tab: posts(返信以外。省略時)/ replies(返信を含む)/ media(画像付き) */
    @GetMapping("/api/users/{handle}/posts")
    PostService.Page posts(@AuthenticationPrincipal LoginUser me, @PathVariable String handle,
            @RequestParam(defaultValue = "posts") String tab, @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        PostService.AuthorTab authorTab = switch (tab) {
            case "posts" -> PostService.AuthorTab.POSTS;
            case "replies" -> PostService.AuthorTab.REPLIES;
            case "media" -> PostService.AuthorTab.MEDIA;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tab の値が正しくありません");
        };
        return service.posts(handle, me.id(), authorTab, before, limit);
    }

    @PutMapping("/api/users/{handle}/follow")
    ProfileService.FollowResult follow(@AuthenticationPrincipal LoginUser me, @PathVariable String handle) {
        return service.follow(me.id(), handle);
    }

    @DeleteMapping("/api/users/{handle}/follow")
    ProfileService.FollowResult unfollow(@AuthenticationPrincipal LoginUser me, @PathVariable String handle) {
        return service.unfollow(me.id(), handle);
    }

    @GetMapping("/api/users/{handle}/following")
    UserSummary.Page following(@AuthenticationPrincipal LoginUser me, @PathVariable String handle,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        return service.following(handle, me.id(), before, limit);
    }

    @GetMapping("/api/users/{handle}/followers")
    UserSummary.Page followers(@AuthenticationPrincipal LoginUser me, @PathVariable String handle,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        return service.followers(handle, me.id(), before, limit);
    }
}
