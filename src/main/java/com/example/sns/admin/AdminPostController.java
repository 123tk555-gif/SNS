package com.example.sns.admin;

import com.example.sns.post.AdminPostService;
import com.example.sns.post.AdminPostService.AdminPost;
import com.example.sns.security.LoginUser;
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
class AdminPostController {

    record AdminPostPage(List<AdminPost> items, int page, int totalPages, long total) {
    }

    record VisibilityRequest(@NotNull(message = "hidden を指定してください") Boolean hidden) {
    }

    private final AdminPostService service;

    AdminPostController(AdminPostService service) {
        this.service = service;
    }

    @GetMapping
    AdminPostPage list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        Page<AdminPost> result = service.search(q, page);
        return new AdminPostPage(result.getContent(), result.getNumber(), result.getTotalPages(),
                result.getTotalElements());
    }

    @PutMapping("/{id}/visibility")
    AdminPost visibility(@AuthenticationPrincipal LoginUser admin, @PathVariable long id,
            @Valid @RequestBody VisibilityRequest request) {
        return service.setHidden(admin.id(), id, request.hidden());
    }
}
