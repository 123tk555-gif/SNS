package com.example.sns.media;

import com.example.sns.apidoc.ApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 画像を配信する(/media/…)。ログイン中のユーザーだけが取れる(SecurityConfig)。
 * 置き場所が S3 のときも、ブラウザには S3 の URL を見せず、アプリが中継する(バケットを公開しないため)。
 */
@RestController
@Tag(name = ApiTags.MEDIA)
class MediaController {

    private static final String PREFIX = "/media/";

    private final ImageStorage storage;

    MediaController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/media/**")
    @Operation(summary = "画像の取得",
            description = "投稿・アイコン・ヘッダーの画像を返す。URL は、投稿やプロフィールの `url`・`avatarUrl`・`headerUrl` に入っている形(`/media/posts/2026/11/〈推測できない名前〉.webp` など)で、"
                    + "ほかの API の応答から取り出して使う。**ログイン中の人だけが取れる**(バケットは公開せず、サーバーが中継する)。"
                    + "ファイル名は推測できず、中身も変わらないので、ブラウザには 30 日覚えてもらう(本人のブラウザだけ)")
    @ApiResponse(responseCode = "200", description = "画像のファイル(JPEG・PNG・WebP・GIF)",
            content = {
                    @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "image/gif", schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "404", description = "画像が見つからない(保存場所の外を指す URL を含む)")
    ResponseEntity<Resource> get(HttpServletRequest request) {
        String path = request.getRequestURI().substring(PREFIX.length());
        Resource image = storage.open(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画像が見つかりません"));
        // ファイル名は推測できず、中身も変わらないので、ブラウザに長く覚えてもらう(本人のブラウザだけ)
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentType(path)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image);
    }
}
