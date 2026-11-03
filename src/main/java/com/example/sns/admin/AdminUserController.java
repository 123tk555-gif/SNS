package com.example.sns.admin;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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

/** 管理者のユーザー管理 API(機能定義書 3.12)。管理者以外が呼ぶと 404(SecurityConfig)。 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = ApiTags.ADMIN_USER)
class AdminUserController {

    @Schema(description = "管理者の画面に出す、ユーザー 1 人分")
    record AdminUserResponse(
            @Schema(description = "ユーザーの ID", example = "7") long id,
            @Schema(description = "ユーザーID(`@` の後ろ)。初期設定の前は `null`", example = "taro_dev", nullable = true) String handle,
            @Schema(description = "表示名。初期設定の前は `null`", example = "たろう", nullable = true) String displayName,
            @Schema(description = "メールアドレス", example = "taro@example.com") String email,
            @Schema(description = "権限。`USER`(一般)か `ADMIN`(管理者)", example = "USER") Role role,
            @Schema(description = "利用停止中か", example = "false") boolean suspended,
            @Schema(description = "投稿の数(返信を含む。削除したものは数えない)", example = "85") long postCount,
            @Schema(description = "登録した日時(UTC)", example = "2026-10-25T05:00:00Z") Instant createdAt) {

        static AdminUserResponse from(User user, long postCount) {
            return new AdminUserResponse(user.getId(), user.getHandle(), user.getDisplayName(), user.getEmail(),
                    user.getRole(), user.isSuspended(), postCount, user.getCreatedAt());
        }
    }

    @Schema(description = "ユーザーの一覧の 1 ページ(ページ番号の方式。ページ番号は 0 から)")
    record AdminUserPage(
            @Schema(description = "ユーザー(登録の新しい順。50 件ずつ)") List<AdminUserResponse> items,
            @Schema(description = "このページの番号(0 から)", example = "0") int page,
            @Schema(description = "ページの数", example = "3") int totalPages,
            @Schema(description = "条件に合うユーザーの総数", example = "120") long total) {
    }

    @Schema(description = "利用停止・解除に送る内容")
    record SuspensionRequest(
            @Schema(description = "`true` で利用停止にする、`false` で解除する", example = "true")
            @NotNull(message = "suspended を指定してください") Boolean suspended) {
    }

    private final AdminUserService service;

    AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "ユーザーの一覧・検索",
            description = "ユーザーID・表示名・メールアドレスの部分一致で探す(`q` を省くと全員)。登録の新しい順に 50 件ずつ。"
                    + "管理者にするには、データベースで `users.role` を `ADMIN` に変える(画面や API からはできない)")
    @ApiResponse(responseCode = "200", description = "ユーザーの一覧(ページ番号の方式)")
    AdminUserPage list(
            @Parameter(description = "検索する言葉(ユーザーID・表示名・メールアドレス)。省略すると全員", example = "taro") @RequestParam(defaultValue = "") String q,
            @Parameter(description = "ページ番号(0 から)", example = "0") @RequestParam(defaultValue = "0") int page) {
        Page<User> result = service.search(q, page);
        java.util.Map<Long, Long> counts = service.postCounts(result.getContent());
        return new AdminUserPage(result.map(u -> AdminUserResponse.from(u, counts.getOrDefault(u.getId(), 0L))).getContent(), result.getNumber(),
                result.getTotalPages(), result.getTotalElements());
    }

    @PutMapping("/{id}/suspension")
    @Operation(summary = "ユーザーの利用停止・解除",
            description = "利用停止にすると、そのユーザーのセッションをすべて消し(すぐにログアウトされる)、リアルタイム配信の接続も切る。"
                    + "投稿は、一覧に出なくなる。自分自身と、他の管理者は停止できない。操作は、いつ・誰が・何をしたかをログに残す")
    @ApiResponse(responseCode = "200", description = "変えた。更新後のユーザーを返す")
    @ApiResponse(responseCode = "400", description = "`suspended` がない、または自分自身・管理者を停止しようとした")
    @ApiResponse(responseCode = "404", description = "ユーザーが見つからない(管理者以外が呼んだときも 404)")
    AdminUserResponse suspend(@AuthenticationPrincipal LoginUser admin,
            @Parameter(description = "ユーザーの ID(ユーザーID ではなく、数字の ID)", example = "7") @PathVariable long id,
            @Valid @RequestBody SuspensionRequest request) {
        User user = service.setSuspended(admin.id(), id, request.suspended());
        return AdminUserResponse.from(user, service.postCounts(List.of(user)).getOrDefault(user.getId(), 0L));
    }
}
