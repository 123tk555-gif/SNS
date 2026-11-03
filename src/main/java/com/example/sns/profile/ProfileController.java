package com.example.sns.profile;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.post.PostService;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = ApiTags.USER)
class ProfileController {

    private static final String HANDLE = "ユーザーID(`@` の後ろ。大文字・小文字は区別しない)";

    private final ProfileService service;

    ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/api/users/{handle}")
    @Operation(summary = "プロフィールの取得",
            description = "ユーザーのプロフィールと、フォロー・ブロック・ミュートの関係(`followedByMe` など)を返す。"
                    + "自分がブロックしている相手は表示するが、投稿の一覧は返さない(画面は「ブロックしています」と表示する)")
    @ApiResponse(responseCode = "200", description = "プロフィール")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    ProfileResponse profile(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "taro_dev") @PathVariable String handle) {
        return service.profile(handle, me.id());
    }

    /** tab: posts(返信以外。省略時)/ replies(返信を含む)/ media(画像付き) */
    @GetMapping("/api/users/{handle}/posts")
    @Operation(summary = "ユーザーの投稿の一覧",
            description = "そのユーザーの投稿を、新しい順に返す。続きは、前の結果の `nextCursor` を `before` に渡して取る。"
                    + "ミュートした人の投稿も、プロフィールを自分で開いたものなので返す。"
                    + "管理者に非表示にされた投稿は、本人が見るときだけ(`hidden: true` の印つきで)含まれる。"
                    + "自分がブロックしている相手の一覧は、空で返す")
    @ApiResponse(responseCode = "200", description = "投稿の一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "400", description = "`tab` の値が正しくない")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    PostService.Page posts(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "taro_dev") @PathVariable String handle,
            @Parameter(description = "`posts`(返信以外)・`replies`(返信を含むすべて)・`media`(画像付き。返信を含む)",
                    schema = @Schema(allowableValues = {"posts", "replies", "media"}, defaultValue = "posts"))
            @RequestParam(defaultValue = "posts") String tab,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "1234") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        PostService.AuthorTab authorTab = switch (tab) {
            case "posts" -> PostService.AuthorTab.POSTS;
            case "replies" -> PostService.AuthorTab.REPLIES;
            case "media" -> PostService.AuthorTab.MEDIA;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tab の値が正しくありません");
        };
        return service.posts(handle, me.id(), authorTab, before, limit);
    }

    @PutMapping("/api/users/{handle}/follow")
    @Operation(summary = "フォロー",
            description = "ユーザーをフォローする。すでにフォロー済みでも、エラーにしない(何度送っても同じ結果)。"
                    + "相手に「フォロー」の通知を作る(付け直しても、通知は増やさない)")
    @ApiResponse(responseCode = "200", description = "フォロー中になった。相手のフォロワーの数を返す")
    @ApiResponse(responseCode = "400", description = "自分自身はフォローできない")
    @ApiResponse(responseCode = "403", description = "自分が相手をブロックしている(相手が自分をブロックしているときは、相手が見えないので 404)")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    ProfileService.FollowResult follow(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        return service.follow(me.id(), handle);
    }

    @DeleteMapping("/api/users/{handle}/follow")
    @Operation(summary = "フォローの解除",
            description = "フォローを解除する。フォローしていなくても、エラーにしない(何度送っても同じ結果)")
    @ApiResponse(responseCode = "200", description = "フォローしていない状態になった。相手のフォロワーの数を返す")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または利用停止中")
    ProfileService.FollowResult unfollow(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        return service.unfollow(me.id(), handle);
    }

    // ---- ブロック・ミュート(機能定義書 3.11) ----

    @PutMapping("/api/users/{handle}/block")
    @Operation(summary = "ブロック",
            description = "ユーザーをブロックする。お互いの投稿が見えなくなり、お互いのフォローが解除される(解除したことは通知しない)。"
                    + "ブロックの関係にある間は、お互いに、フォロー・返信・いいね・通知を送ることができない。"
                    + "すでにあるいいね・返信は消さない(ブロックを解除したら、また見える)。何度送っても同じ結果")
    @ApiResponse(responseCode = "204", description = "ブロックした")
    @ApiResponse(responseCode = "400", description = "自分自身はブロックできない")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    ResponseEntity<Void> block(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        service.block(me.id(), handle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{handle}/block")
    @Operation(summary = "ブロックの解除",
            description = "自分がしたブロックを解除する(相手からもブロックされていても解除できる)。何度送っても同じ結果")
    @ApiResponse(responseCode = "204", description = "解除した(ブロックしていなくても、エラーにしない)")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        service.unblock(me.id(), handle);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/users/{handle}/mute")
    @Operation(summary = "ミュート",
            description = "ユーザーをミュートする。その人の投稿が、自分のタイムライン・検索・タグ・ブックマークなどの一覧に出なくなり、"
                    + "その人からの通知も作られなくなる。相手には何も知らされない。何度送っても同じ結果")
    @ApiResponse(responseCode = "204", description = "ミュートした")
    @ApiResponse(responseCode = "400", description = "自分自身はミュートできない")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    ResponseEntity<Void> mute(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        service.mute(me.id(), handle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{handle}/mute")
    @Operation(summary = "ミュートの解除", description = "ミュートを解除する。何度送っても同じ結果")
    @ApiResponse(responseCode = "204", description = "解除した(ミュートしていなくても、エラーにしない)")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない")
    ResponseEntity<Void> unmute(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "hanako") @PathVariable String handle) {
        service.unmute(me.id(), handle);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/blocks")
    @Operation(summary = "ブロックしているユーザーの一覧",
            description = "ブロックした新しい順に、すべて返す(`nextCursor` はいつも `null`)")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧")
    UserSummary.Page blocks(@AuthenticationPrincipal LoginUser me) {
        return new UserSummary.Page(service.blockedUsers(me.id()), null);
    }

    @GetMapping("/api/me/mutes")
    @Operation(summary = "ミュートしているユーザーの一覧",
            description = "ミュートした新しい順に、すべて返す(`nextCursor` はいつも `null`)")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧")
    UserSummary.Page mutes(@AuthenticationPrincipal LoginUser me) {
        return new UserSummary.Page(service.mutedUsers(me.id()), null);
    }

    @GetMapping("/api/users/{handle}/following")
    @Operation(summary = "フォロー中のユーザーの一覧",
            description = "そのユーザーがフォローしている人を、フォローした新しい順に返す。続きは、前の結果の `nextCursor` を `before` に渡して取る。"
                    + "利用停止中のユーザーと、ブロックの関係にあるユーザーは出ない")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    UserSummary.Page following(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "taro_dev") @PathVariable String handle,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "40") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return service.following(handle, me.id(), before, limit);
    }

    @GetMapping("/api/users/{handle}/followers")
    @Operation(summary = "フォロワーの一覧",
            description = "そのユーザーをフォローしている人を、フォローした新しい順に返す。続きは、前の結果の `nextCursor` を `before` に渡して取る。"
                    + "利用停止中のユーザーと、ブロックの関係にあるユーザーは出ない")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、利用停止中、または相手が自分をブロックしている")
    UserSummary.Page followers(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = HANDLE, example = "taro_dev") @PathVariable String handle,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "40") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return service.followers(handle, me.id(), before, limit);
    }
}
