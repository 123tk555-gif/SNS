package com.example.sns.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 認証の API が受け取る JSON。パスワードの長さなどの細かいルールは AuthService で確かめる。 */
final class AuthRequests {

    private AuthRequests() {
    }

    record Signup(
            @NotBlank(message = "メールアドレスを入力してください")
            @Email(message = "メールアドレスの形で入力してください")
            @Size(max = 254, message = "メールアドレスの形で入力してください")
            String email,
            @NotNull(message = "パスワードを入力してください")
            String password) {
    }

    record Login(
            @NotBlank(message = "メールアドレスを入力してください") String email,
            @NotBlank(message = "パスワードを入力してください") String password) {
    }

    record EmailOnly(@NotBlank(message = "メールアドレスを入力してください") String email) {
    }

    record Token(@NotBlank(message = "リンクが正しくありません") String token) {
    }

    record NewPassword(
            @NotBlank(message = "リンクが正しくありません") String token,
            @NotNull(message = "パスワードを入力してください") String password) {
    }

    record Onboarding(
            @NotNull(message = "ユーザーIDを入力してください") String handle,
            @NotBlank(message = "表示名は1〜50文字で入力してください")
            @Size(max = 50, message = "表示名は1〜50文字で入力してください")
            String displayName) {
    }
}
