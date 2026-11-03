package com.example.sns.admin;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.post.AdminPostService;
import com.example.sns.post.AdminPostService.AdminPost;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理者の投稿管理 API(機能定義書 3.12)。管理者以外が呼ぶと 404(SecurityConfig)。 */
@RestController
@RequestMapping("/api/admin/posts")
@Tag(name = ApiTags.ADMIN_POST)
class AdminPostController {

    @Schema(description = "投稿の一覧の 1 ページ(ページ番号の方式。ページ番号は 0 から)")
    record AdminPostPage(
            @Schema(description = "投稿(新しい順。50 件ずつ)") List<AdminPost> items,
            @Schema(description = "このページの番号(0 から)", example = "0") int page,
            @Schema(description = "ページの数", example = "3") int totalPages,
            @Schema(description = "条件に合う投稿の総数", example = "120") long total) {
    }

    @Schema(description = "非表示・解除に送る内容")
    record VisibilityRequest(
            @Schema(description = "`true` で非表示にする、`false` で表示に戻す", example = "true")
            @NotNull(message = "hidden を指定してください") Boolean hidden) {
    }

    private final AdminPostService service;

    AdminPostController(AdminPostService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "投稿の一覧・検索",
            description = "投稿の本文を、部分一致で探す(`q` を省くと全部。`%` と `_` はふつうの文字として探す)。新しい順に 50 件ずつ。"
                    + "非表示の投稿と、利用停止中のユーザーの投稿も含め、それぞれ印(`hidden`・`authorSuspended`)を付ける。削除済みの投稿は含めない")
    @ApiResponse(responseCode = "200", description = "投稿の一覧(ページ番号の方式)")
    AdminPostPage list(
            @Parameter(description = "検索する言葉(本文)。省略すると全部", example = "カフェ") @RequestParam(defaultValue = "") String q,
            @Parameter(description = "ページ番号(0 から)", example = "0") @RequestParam(defaultValue = "0") int page) {
        Page<AdminPost> result = service.search(q, page);
        return new AdminPostPage(result.getContent(), result.getNumber(), result.getTotalPages(),
                result.getTotalElements());
    }

    @PutMapping("/{id}/visibility")
    @Operation(summary = "投稿の非表示・表示に戻す",
            description = "非表示にすると、投稿者以外には見えなくなり、リアルタイム配信(`post-deleted`)で、開いている画面から消える。"
                    + "投稿者本人には、自分のプロフィールの投稿一覧にだけ「管理者により非表示になりました」の印つきで出る。"
                    + "操作は、いつ・誰が・何をしたかをログに残す")
    @ApiResponse(responseCode = "200", description = "変えた。更新後の投稿(管理者向けの形)を返す")
    @ApiResponse(responseCode = "400", description = "`hidden` がない")
    @ApiResponse(responseCode = "404", description = "投稿が見つからない・削除済み(管理者以外が呼んだときも 404)")
    AdminPost visibility(@AuthenticationPrincipal LoginUser admin,
            @Parameter(description = "投稿の ID", example = "1234") @PathVariable long id,
            @Valid @RequestBody VisibilityRequest request) {
        return service.setHidden(admin.id(), id, request.hidden());
    }
}
