package com.example.sns.realtime;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE の接続(機能定義書 3.8)。画面はログイン後に new EventSource('/api/stream') でつなぎ、つなぎっぱなしにする */
@RestController
@Tag(name = ApiTags.STREAM)
class StreamController {

    private final StreamRegistry registry;

    StreamController(StreamRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "リアルタイム配信につなぐ(SSE)",
            description = "ログイン後に、ブラウザの `new EventSource('/api/stream')` でつなぎ、ログイン中はつなぎっぱなしにする。"
                    + "新しい投稿・削除された投稿・通知が、再読み込みなしで届く。\n\n"
                    + "| イベント名 | いつ | 送る相手 | 中身 |\n|---|---|---|---|\n"
                    + "| `post-created` | 返信以外の投稿が作られた | 接続中の全員(見えない人と、投稿者本人を除く) | 投稿(`PostResponse`)と、投稿者を自分がフォローしているか(`followedByMe`) |\n"
                    + "| `post-deleted` | 投稿が削除・非表示にされた | 接続中の全員 | `{\"id\": 1234}` |\n"
                    + "| `notification` | 通知が作られた | 受け取る人 | `{\"unreadCount\": 4, \"notification\": {…通知…}}` |\n\n"
                    + "1 人が複数のタブで開いてもよい(1 人 5 接続まで。超えたら、古いものから切る)。切れたら、ブラウザが自動でつなぎ直す"
                    + "(つなぎ直したときの取りこぼしは、`GET /api/timeline?after=` と `GET /api/notifications/unread-count` で取る)。"
                    + "**この画面の「Try it out」では、つなぎっぱなしになるため、動きを確かめられない**")
    @ApiResponse(responseCode = "200", description = "つなぎっぱなしのイベントの流れ(`text/event-stream`)",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string"),
                    examples = @ExampleObject(value = "event:post-deleted\ndata:{\"id\":1234}\n\n")))
    SseEmitter stream(@AuthenticationPrincipal LoginUser me) {
        return registry.open(me.id());
    }
}
