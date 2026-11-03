package com.example.sns.notification;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知の API(機能定義書 3.7)。 */
@RestController
@Tag(name = ApiTags.NOTIFICATION)
class NotificationController {

    @Schema(description = "未読の通知の数")
    record UnreadCount(
            @Schema(description = "未読の通知の数", example = "3") long unreadCount) {
    }

    @Schema(description = "既読にする範囲")
    record ReadRequest(
            @Schema(description = "この ID 以下の、自分の通知を、すべて既読にする(通知の一覧の先頭の `id` を送る)", example = "501")
            @NotNull(message = "upToId を指定してください") Long upToId) {
    }

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "通知の一覧",
            description = "自分への通知を、新しい順に返す。通知は、いいね(`LIKE`)・返信(`REPLY`)・フォロー(`FOLLOW`)・メンション(`MENTION`)の 4 種類。"
                    + "対象の投稿が削除・非表示になった通知、操作した人が利用停止中の通知、ブロック・ミュートの関係にある人の通知は出ない。"
                    + "続きは、前の結果の `nextCursor` を `before` に渡して取る")
    @ApiResponse(responseCode = "200", description = "通知の一覧。`nextCursor` が `null` なら、それ以上はない")
    NotificationResponse.Page list(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`", example = "480") @RequestParam(required = false) Long before,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return service.page(me.id(), before, limit);
    }

    @GetMapping("/api/notifications/unread-count")
    @Operation(summary = "未読の通知の数", description = "未読の通知の数を返す。ログイン直後の値は `GET /api/auth/me` にも含まれる")
    @ApiResponse(responseCode = "200", description = "未読の数")
    UnreadCount unreadCount(@AuthenticationPrincipal LoginUser me) {
        return new UnreadCount(service.unreadCount(me.id()));
    }

    /** upToId 以下の自分の通知を、すべて既読にする */
    @PostMapping("/api/notifications/read")
    @Operation(summary = "通知を既読にする", description = "`upToId` 以下の ID の、自分の通知を、すべて既読にする。既読にしたあとの未読の数を返す")
    @ApiResponse(responseCode = "200", description = "既読にした。未読の数(この操作のあと)を返す")
    @ApiResponse(responseCode = "400", description = "`upToId` がない")
    UnreadCount read(@AuthenticationPrincipal LoginUser me, @Valid @RequestBody ReadRequest request) {
        return new UnreadCount(service.markRead(me.id(), request.upToId()));
    }
}
