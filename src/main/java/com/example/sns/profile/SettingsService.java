package com.example.sns.profile;

import com.example.sns.auth.GuestService;
import com.example.sns.media.ImageStorage;
import com.example.sns.user.User;
import com.example.sns.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 自分のプロフィールの編集(F-PROF-02。設定画面 S-14)。 */
@Service
@Transactional
public class SettingsService {

    static final int MAX_DISPLAY_NAME = 50;
    static final int MAX_BIO = 160;

    private final UserRepository users;
    private final ImageStorage storage;

    public SettingsService(UserRepository users, ImageStorage storage) {
        this.users = users;
        this.storage = storage;
    }

    /** 表示名(1〜50 文字)と自己紹介(160 文字まで。空なら未設定) */
    public User updateProfile(long userId, String displayName, String bio) {
        String name = displayName == null ? "" : displayName.strip();
        String about = bio == null ? "" : bio.strip();
        if (name.isEmpty() || length(name) > MAX_DISPLAY_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表示名は1〜50文字で入力してください");
        }
        if (length(about) > MAX_BIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自己紹介は160文字以内で入力してください");
        }
        User user = find(userId);
        user.updateProfile(name, about);
        return user;
    }

    /** アイコン画像を変える(画像のルールは投稿と同じ。古いファイルは、保存が確定したあとで消す) */
    public User changeAvatar(long userId, MultipartFile file) {
        return changeImage(userId, file, "avatars", User::getAvatarPath, User::setAvatarPath);
    }

    public User removeAvatar(long userId) {
        return changeImage(userId, null, "avatars", User::getAvatarPath, User::setAvatarPath);
    }

    public User changeHeader(long userId, MultipartFile file) {
        return changeImage(userId, file, "headers", User::getHeaderPath, User::setHeaderPath);
    }

    public User removeHeader(long userId) {
        return changeImage(userId, null, "headers", User::getHeaderPath, User::setHeaderPath);
    }

    /** file が null なら画像を外す */
    private User changeImage(long userId, MultipartFile file, String folder, Function<User, String> current,
            BiConsumer<User, String> update) {
        User user = find(userId);
        String old = current.apply(user);
        String path = null;
        if (file != null) {
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ImageStorage.WRONG_FORMAT);
            }
            path = storage.store(file, folder).path();
            List<String> saved = new ArrayList<>(List.of(path));
            storage.deleteOnRollback(saved);
        }
        update.accept(user, path);
        if (old != null) {
            storage.deleteAfterCommit(List.of(old));
        }
        return user;
    }

    /** 変える人を探す。お試しのアカウントは、みんなで使うので、プロフィールを変えられない(403) */
    private User find(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください"));
        if (user.isGuest()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, GuestService.CANNOT_EDIT);
        }
        return user;
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
