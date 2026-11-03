package com.example.sns.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** エラーのときに返す JSON(機能定義書 1.4)。errors は入力チェックのときだけ中身が入る。 */
@Schema(description = "エラーのときに返す JSON。`message` は、そのまま画面に出せる日本語")
public record ApiError(
        @Schema(description = "エラーの内容(画面にそのまま出せる日本語)", example = "入力内容に誤りがあります") String message,
        @Schema(description = "入力チェックのエラーの一覧。入力チェックのとき以外は、空の配列") List<FieldProblem> errors) {

    public static final String NOT_FOUND_MESSAGE = "ページが見つかりません";

    @Schema(description = "入力チェックのエラー 1 件")
    public record FieldProblem(
            @Schema(description = "エラーになった項目の名前", example = "text") String field,
            @Schema(description = "その項目のエラーの内容", example = "1000文字以内で入力してください") String message) {
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
