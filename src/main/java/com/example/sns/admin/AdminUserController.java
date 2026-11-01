package com.example.sns.admin;

import com.example.sns.security.LoginUser;
import com.example.sns.user.Role;
import com.example.sns.user.User;
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
class AdminUserController {

    record AdminUserResponse(long id, String handle, String displayName, String email, Role role, boolean suspended,
            Instant createdAt) {

        static AdminUserResponse from(User user) {
            return new AdminUserResponse(user.getId(), user.getHandle(), user.getDisplayName(), user.getEmail(),
                    user.getRole(), user.isSuspended(), user.getCreatedAt());
        }
    }

    record AdminUserPage(List<AdminUserResponse> items, int page, int totalPages, long total) {
    }

    record SuspensionRequest(@NotNull(message = "suspended を指定してください") Boolean suspended) {
    }

    private final AdminUserService service;

    AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    AdminUserPage list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        Page<User> result = service.search(q, page);
        return new AdminUserPage(result.map(AdminUserResponse::from).getContent(), result.getNumber(),
                result.getTotalPages(), result.getTotalElements());
    }

    @PutMapping("/{id}/suspension")
    AdminUserResponse suspend(@AuthenticationPrincipal LoginUser admin, @PathVariable long id,
            @Valid @RequestBody SuspensionRequest request) {
        return AdminUserResponse.from(service.setSuspended(admin.id(), id, request.suspended()));
    }
}
