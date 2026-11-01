package com.example.sns.common;

import java.util.List;

/** エラーのときに返す JSON(機能定義書 1.4)。errors は入力チェックのときだけ中身が入る。 */
public record ApiError(String message, List<FieldProblem> errors) {

    public static final String NOT_FOUND_MESSAGE = "ページが見つかりません";

    public record FieldProblem(String field, String message) {
    }

    public static ApiError of(String message) {
        return new ApiError(message, List.of());
    }

    /** Spring Security のフィルターの中など、Jackson を通さずに書くとき用(message は決まった文だけを渡す) */
    public static String json(String message) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"message\":\"" + escaped + "\",\"errors\":[]}";
    }
}
