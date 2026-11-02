package com.example.sns.profile;

import com.example.sns.security.LoginUser;
import com.example.sns.user.User;
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
class SettingsController {

    record ProfileRequest(String displayName, String bio) {
    }

    private final SettingsService settings;
    private final ProfileService profiles;

    SettingsController(SettingsService settings, ProfileService profiles) {
        this.settings = settings;
        this.profiles = profiles;
    }

    @PutMapping("/api/me/profile")
    ProfileResponse updateProfile(@AuthenticationPrincipal LoginUser me, @RequestBody ProfileRequest request) {
        return profile(settings.updateProfile(me.id(), request.displayName(), request.bio()), me);
    }

    @PutMapping(value = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileResponse changeAvatar(@AuthenticationPrincipal LoginUser me, @RequestPart("file") MultipartFile file) {
        return profile(settings.changeAvatar(me.id(), file), me);
    }

    @DeleteMapping("/api/me/avatar")
    ProfileResponse removeAvatar(@AuthenticationPrincipal LoginUser me) {
        return profile(settings.removeAvatar(me.id()), me);
    }

    @PutMapping(value = "/api/me/header", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileResponse changeHeader(@AuthenticationPrincipal LoginUser me, @RequestPart("file") MultipartFile file) {
        return profile(settings.changeHeader(me.id(), file), me);
    }

    @DeleteMapping("/api/me/header")
    ProfileResponse removeHeader(@AuthenticationPrincipal LoginUser me) {
        return profile(settings.removeHeader(me.id()), me);
    }

    private ProfileResponse profile(User user, LoginUser me) {
        return profiles.profile(user.getHandle(), me.id());
    }
}
