package com.example.sns.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 認証の API が受け取る JSON。パスワードの長さなどの細かいルールは AuthService で確かめる。 */
final class AuthRequests {

    private AuthRequests() {
    }

    @Schema(description = "登録に送る内容")
    record Signup(
            @Schema(description = "メールアドレス(254 文字以内。大文字・小文字は区別しない)", example = "taro@example.com")
            @NotBlank(message = "メールアドレスを入力してください")
            @Email(message = "メールアドレスの形で入力してください")
            @Size(max = 254, message = "メールアドレスの形で入力してください")
            String email,
            @Schema(description = "パスワード(8〜72 文字)", example = "example-pass-123", format = "password")
            @NotNull(message = "パスワードを入力してください")
            String password) {
    }

    @Schema(description = "ログインに送る内容")
    record Login(
            @Schema(description = "メールアドレス", example = "taro@example.com")
            @NotBlank(message = "メールアドレスを入力してください") String email,
            @Schema(description = "パスワード", example = "example-pass-123", format = "password")
            @NotBlank(message = "パスワードを入力してください") String password) {
    }

    @Schema(description = "メールアドレスだけを送る内容(確認メールの再送・パスワード再設定メールの送信)")
    record EmailOnly(
            @Schema(description = "メールアドレス", example = "taro@example.com")
            @NotBlank(message = "メールアドレスを入力してください") String email) {
    }

    @Schema(description = "メールのリンクの値を送る内容(メールアドレスの確認)")
    record Token(
            @Schema(description = "メールのリンク(`?token=…`)の値", example = "k3Jx0b...(推測できない長い文字列)")
            @NotBlank(message = "リンクが正しくありません") String token) {
    }

    @Schema(description = "新しいパスワードを送る内容")
    record NewPassword(
            @Schema(description = "再設定メールのリンク(`?token=…`)の値", example = "k3Jx0b...(推測できない長い文字列)")
            @NotBlank(message = "リンクが正しくありません") String token,
            @Schema(description = "新しいパスワード(8〜72 文字)", example = "example-new-pass-456", format = "password")
            @NotNull(message = "パスワードを入力してください") String password) {
    }

    @Schema(description = "初期設定に送る内容")
    record Onboarding(
            @Schema(description = "ユーザーID。半角英数字と `_` の 3〜15 文字。大文字・小文字は区別せずに重複を確かめる(表示は入力したとおり)。あとから変えられない",
                    example = "taro_dev")
            @NotNull(message = "ユーザーIDを入力してください") String handle,
            @Schema(description = "表示名(1〜50 文字)", example = "たろう")
            @NotBlank(message = "表示名は1〜50文字で入力してください")
            @Size(max = 50, message = "表示名は1〜50文字で入力してください")
            String displayName) {
    }
}
