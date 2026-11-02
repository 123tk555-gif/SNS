package com.example.sns.realtime;

import com.example.sns.security.LoginUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE の接続(機能定義書 3.8)。画面はログイン後に new EventSource('/api/stream') でつなぎ、つなぎっぱなしにする */
@RestController
class StreamController {

    private final StreamRegistry registry;

    StreamController(StreamRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal LoginUser me) {
        return registry.open(me.id());
    }
}
