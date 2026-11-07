package com.example.sns.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 画像の場所(/media/… の後ろ)として受け付けるパスの単体テスト。
 * アプリが作る形(英小文字・数字のフォルダ + UUID のファイル名)以外は、保存場所の外を指す危険があるので断る。
 */
class ImagePathTest {

    private static final String UUID = "3f9c1a7e-0b1d-4c55-9e0a-2b6f4c7d8e91";

    @Test
    void アプリが作る形のパスは受け付ける() {
        assertThat(ImageStorage.isValid("posts/2026/11/" + UUID + ".webp")).isTrue();
        assertThat(ImageStorage.isValid("avatars/" + UUID + ".png")).isTrue();
        assertThat(ImageStorage.isValid("headers/" + UUID + ".jpg")).isTrue();
    }

    @Test
    void 保存場所の外を指すパスは断る() {
        assertThat(ImageStorage.isValid("../" + UUID + ".png")).isFalse();
        assertThat(ImageStorage.isValid("posts/../../etc/" + UUID + ".png")).isFalse();
        assertThat(ImageStorage.isValid("/etc/" + UUID + ".png")).isFalse();
        assertThat(ImageStorage.isValid("posts\\..\\" + UUID + ".png")).isFalse();
    }

    @Test
    void 形の違うパスは断る() {
        assertThat(ImageStorage.isValid(null)).isFalse();
        assertThat(ImageStorage.isValid("")).isFalse();
        assertThat(ImageStorage.isValid(UUID + ".png")).isFalse(); // フォルダがない
        assertThat(ImageStorage.isValid("posts/photo.png")).isFalse(); // 推測できる名前
        assertThat(ImageStorage.isValid("Posts/" + UUID + ".png")).isFalse(); // 大文字のフォルダ
        assertThat(ImageStorage.isValid("posts/" + UUID)).isFalse(); // 拡張子がない
        assertThat(ImageStorage.isValid("posts/" + UUID + ".png/")).isFalse();
    }
}
