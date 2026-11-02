package com.example.sns.media;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 画像を配信する(/media/…)。ログイン中のユーザーだけが取れる(SecurityConfig)。 */
@RestController
class MediaController {

    private static final String PREFIX = "/media/";

    private final ImageStorage storage;

    MediaController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/media/**")
    ResponseEntity<Resource> get(HttpServletRequest request) {
        String path = request.getRequestURI().substring(PREFIX.length());
        Path file = storage.resolve(path);
        if (file == null || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "画像が見つかりません");
        }
        // ファイル名は推測できず、中身も変わらないので、ブラウザに長く覚えてもらう(本人のブラウザだけ)
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentType(file)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
