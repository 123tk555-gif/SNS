package com.example.sns.profile;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import com.example.sns.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 自分のプロフィールの編集の API(機能定義書 3.2)。どれも、変えたあとのプロフィールを返す */
@RestController
@Tag(name = ApiTags.SETTINGS)
class SettingsController {

    @Schema(description = "プロフィールの更新に送る内容")
    record ProfileRequest(
            @Schema(description = "表示名(必須。1〜50 文字。前後の空白は取り除く)", example = "たろう") String displayName,
            @Schema(description = "自己紹介(160 文字以内。空にすると未設定になる)", example = "Java を勉強中です", nullable = true) String bio) {
    }

    /** 画像の変更の、multipart/form-data の項目(API 仕様書の説明用) */
    @Schema(name = "ImageForm", description = "画像の変更に送る内容(`multipart/form-data`)")
    record ImageForm(
            @Schema(description = "画像ファイル(5MB まで。JPEG・PNG・WebP・GIF。形式は拡張子ではなく中身で確かめる)", type = "string", format = "binary")
            String file) {
    }

    private final SettingsService settings;
    private final ProfileService profiles;

    SettingsController(SettingsService settings, ProfileService profiles) {
        this.settings = settings;
        this.profiles = profiles;
    }

    @PutMapping("/api/me/profile")
    @Operation(summary = "プロフィールの更新(表示名・自己紹介)",
            description = "自分の表示名と自己紹介を変える。変えたあとのプロフィールを返す。ユーザーIDは変えられない")
    @ApiResponse(responseCode = "200", description = "更新した。更新後のプロフィールを返す")
    @ApiResponse(responseCode = "400", description = "表示名が 1〜50 文字でない、または自己紹介が 160 文字を超える")
    ProfileResponse updateProfile(@AuthenticationPrincipal LoginUser me, @RequestBody ProfileRequest request) {
        return profile(settings.updateProfile(me.id(), request.displayName(), request.bio()), me);
    }

    @PutMapping(value = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "アイコン画像の変更",
            description = "アイコン画像を変える。古い画像ファイルは消す。正方形には切り取らず、画面で丸く表示する",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = ImageForm.class))))
    @ApiResponse(responseCode = "200", description = "変えた。更新後のプロフィールを返す")
    @ApiResponse(responseCode = "400", description = "画像でないファイル、または画像を読み込めない")
    @ApiResponse(responseCode = "413", description = "画像が 5MB を超える")
    ProfileResponse changeAvatar(@AuthenticationPrincipal LoginUser me,
            @Parameter(hidden = true) @RequestPart("file") MultipartFile file) {
        return profile(settings.changeAvatar(me.id(), file), me);
    }

    @DeleteMapping("/api/me/avatar")
    @Operation(summary = "アイコン画像の削除", description = "アイコン画像を消して、未設定に戻す(画面には、表示名の 1 文字目を色つきの丸で出す)。画像ファイルも消す")
    @ApiResponse(responseCode = "200", description = "消した。更新後のプロフィールを返す")
    ProfileResponse removeAvatar(@AuthenticationPrincipal LoginUser me) {
        return profile(settings.removeAvatar(me.id()), me);
    }

    @PutMapping(value = "/api/me/header", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "ヘッダー画像の変更",
            description = "プロフィールの上に出すヘッダー画像を変える。古い画像ファイルは消す",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = ImageForm.class))))
    @ApiResponse(responseCode = "200", description = "変えた。更新後のプロフィールを返す")
    @ApiResponse(responseCode = "400", description = "画像でないファイル、または画像を読み込めない")
    @ApiResponse(responseCode = "413", description = "画像が 5MB を超える")
    ProfileResponse changeHeader(@AuthenticationPrincipal LoginUser me,
            @Parameter(hidden = true) @RequestPart("file") MultipartFile file) {
        return profile(settings.changeHeader(me.id(), file), me);
    }

    @DeleteMapping("/api/me/header")
    @Operation(summary = "ヘッダー画像の削除", description = "ヘッダー画像を消して、未設定に戻す。画像ファイルも消す")
    @ApiResponse(responseCode = "200", description = "消した。更新後のプロフィールを返す")
    ProfileResponse removeHeader(@AuthenticationPrincipal LoginUser me) {
        return profile(settings.removeHeader(me.id()), me);
    }

    private ProfileResponse profile(User user, LoginUser me) {
        return profiles.profile(user.getHandle(), me.id());
    }
}
