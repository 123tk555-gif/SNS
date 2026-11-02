package com.example.sns.notification;

import com.example.sns.security.LoginUser;
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
class NotificationController {

    record UnreadCount(long unreadCount) {
    }

    record ReadRequest(@NotNull(message = "upToId を指定してください") Long upToId) {
    }

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications")
    NotificationResponse.Page list(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        return service.page(me.id(), before, limit);
    }

    @GetMapping("/api/notifications/unread-count")
    UnreadCount unreadCount(@AuthenticationPrincipal LoginUser me) {
        return new UnreadCount(service.unreadCount(me.id()));
    }

    /** upToId 以下の自分の通知を、すべて既読にする */
    @PostMapping("/api/notifications/read")
    UnreadCount read(@AuthenticationPrincipal LoginUser me, @Valid @RequestBody ReadRequest request) {
        return new UnreadCount(service.markRead(me.id(), request.upToId()));
    }
}
