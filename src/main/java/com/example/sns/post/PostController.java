package com.example.sns.post;

import com.example.sns.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 投稿とタイムラインの API(機能定義書 3.3・3.5)。 */
@RestController
class PostController {

    record EditRequest(String text) {
    }

    private final PostService service;

    PostController(PostService service) {
        this.service = service;
    }

    /**
     * 投稿の作成。multipart/form-data で text・images(複数)・alts(画像と同じ順番・同じ数)を送る。
     * alts は「,」を含んでも分かれないよう、送られたとおりの値をそのまま受け取る。
     */
    @PostMapping(value = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PostResponse> create(@AuthenticationPrincipal LoginUser me,
            @RequestParam(defaultValue = "") String text,
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            HttpServletRequest request) {
        String[] alts = request.getParameterValues("alts");
        PostResponse created = service.create(me.id(), text, images, alts == null ? List.of() : Arrays.asList(alts));
        return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
    }

    @GetMapping("/api/posts/{id}")
    PostResponse get(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        return service.get(id, me.id());
    }

    @PutMapping("/api/posts/{id}")
    PostResponse edit(@AuthenticationPrincipal LoginUser me, @PathVariable long id, @RequestBody EditRequest request) {
        return service.edit(id, me.id(), request.text());
    }

    @DeleteMapping("/api/posts/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser me, @PathVariable long id) {
        service.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }

    /** タイムライン。tab は今は all(すべて)だけ。following(フォロー中)は M5 で作る */
    @GetMapping("/api/timeline")
    PostService.Page timeline(@AuthenticationPrincipal LoginUser me,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        if (!tab.equals("all")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tab の値が正しくありません");
        }
        return service.timeline(me.id(), before, limit);
    }
}
