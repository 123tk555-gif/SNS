package com.example.sns.apidoc;

import com.example.sns.common.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;
import java.util.TreeMap;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 仕様書(OpenAPI)の全体の設定。Swagger UI(/swagger-ui.html)と /v3/api-docs に出る。
 * 各 API の説明は、コントローラーの @Operation・@ApiResponse、データの形の @Schema に書く。
 * ここには、全体で共通のもの(概要・認証の定義・タグ・共通のエラー応答)だけを置く。
 */
@Configuration
class OpenApiConfig {

    /** セッションの Cookie の、認証の定義の名前 */
    static final String SESSION_SCHEME = "sessionCookie";


    private static final String DESCRIPTION = """
            SNS タイムラインアプリの API 仕様書です。詳しい仕様(処理の流れ・データの定義・権限)は
            [機能定義書](https://github.com/123tk555-gif/SNS/blob/main/docs/機能定義書.md)にあります。

            ## 試し方(「Try it out」)
            1. **アプリでログインする。** この画面と同じアドレスでアプリを開き、ログインしておきます(ログイン状態は Cookie `SESSION` で、ブラウザが自動で付けます)。
            2. この画面で、試したい API の「Try it out」→「Execute」を押します。
            3. データを変える API(POST・PUT・DELETE)に必要な CSRF のヘッダー `X-XSRF-TOKEN` は、この画面が Cookie `XSRF-TOKEN` から自動で付けます。

            ## 共通のルール
            - データは JSON でやり取りします(画像を送るときだけ `multipart/form-data`)。日時は ISO 8601 の UTC です。
            - **エラー**は、ステータスコードと `{ "message": "画面にそのまま出せる日本語", "errors": [ { "field": "text", "message": "…" } ] }` の形で返します(`errors` は入力チェックのときだけ中身が入ります)。
            - **一覧の続きの読み込み(カーソル方式)**: 新しい順の一覧は、前の結果の `nextCursor` を `before` に渡して続きを取ります(`nextCursor` が `null` なら、それ以上はありません)。返信のように古い順の一覧は `after` を使います。`limit` は 1〜50、省略すると 20 件です。
            - **見えない投稿**: 削除・非表示の投稿、利用停止中のユーザーの投稿、ブロックの関係にある人の投稿は、一覧に出ません。ミュートした人の投稿は、一覧から除きます(その人のプロフィールと投稿詳細では見られます)。
            - ステータスコード: 200 成功 / 201 作成 / 204 成功(本文なし) / 400 入力が正しくない / 401 ログインしていない / 403 権限がない・CSRF の値が違う / 404 見つからない / 409 重複 / 413 大きすぎる / 423 ログインのロック中 / 429 回数が多すぎる
            """;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SNS タイムラインアプリ API 仕様書")
                        .version("1.0")
                        .description(DESCRIPTION))
                // 画面を開いたアドレスと同じ場所を指す(手元でも、公開したサーバーでも、そのまま動く)
                .servers(List.of(new Server().url("/").description("このサーバー")))
                .tags(List.of(
                        tag(ApiTags.AUTH, "登録・ログイン・ログアウト・パスワードの再設定・初期設定。ログイン不要のものと、ログイン中の自分の情報を返すものがある(機能定義書 3.1)"),
                        tag(ApiTags.USER, "プロフィール・フォロー・ブロック・ミュート(機能定義書 3.2、3.6、3.11)"),
                        tag(ApiTags.SETTINGS, "自分の表示名・自己紹介・アイコン画像・ヘッダー画像の変更(機能定義書 3.2)"),
                        tag(ApiTags.POST, "投稿・返信の作成、編集、削除、タイムライン(機能定義書 3.3、3.5)"),
                        tag(ApiTags.REACTION, "いいね・ブックマーク(機能定義書 3.4)"),
                        tag(ApiTags.NOTIFICATION, "通知の一覧・未読の数・既読(機能定義書 3.7)"),
                        tag(ApiTags.SEARCH, "投稿・ユーザー・ハッシュタグの検索、トレンド(機能定義書 3.9、3.10)"),
                        tag(ApiTags.STREAM, "新着の投稿・通知を、再読み込みなしで届ける(Server-Sent Events。機能定義書 3.8)。"
                                + "送られるイベント: `post-created`(新しい投稿)、`post-deleted`(削除・非表示された投稿の ID)、"
                                + "`notification`(新しい通知と未読の数)。接続の維持のため、30 秒ごとに中身のない合図(コメント行)も送る"),
                        tag(ApiTags.MEDIA, "投稿・アイコン・ヘッダーの画像の配信。ログイン中の人だけが取れる"),
                        tag(ApiTags.ADMIN_USER, "管理者だけが使える。管理者以外が呼ぶと、存在しない URL と同じ 404 を返す(機能定義書 3.12)"),
                        tag(ApiTags.ADMIN_POST, "管理者だけが使える。管理者以外が呼ぶと、存在しない URL と同じ 404 を返す(機能定義書 3.12)")))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")
                                .description("ログイン(`POST /api/auth/login` か Google ログイン)でサーバーが渡す Cookie。"
                                        + "ブラウザが自動で付けるので、この画面の「Authorize」では設定できない。先にアプリでログインしておく")))
                // 既定ではログインが必要。ログイン不要の API は、コントローラーの @SecurityRequirements で外している
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }

    /** どの API にも共通のエラー応答(401・403・管理者の 404)を足し、エラーの JSON の形 ApiError を 1 つだけ定義する */
    @Bean
    OpenApiCustomizer commonErrorResponses() {
        return openApi -> {
            Map<String, Schema> apiError = ModelConverters.getInstance(true).readAll(ApiError.class);
            apiError.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            Schema<?> ref = new Schema<>().$ref("#/components/schemas/ApiError");

            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean needsLogin = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
                if (needsLogin) {
                    add(operation, "401", "ログインしていない(セッションがない・切れた)", ref);
                }
                if (method != io.swagger.v3.oas.models.PathItem.HttpMethod.GET && needsLogin) {
                    add(operation, "403", "権限がない、または CSRF の値(ヘッダー `X-XSRF-TOKEN`)がない・違う", ref);
                }
                if (path.startsWith("/api/admin/")) {
                    add(operation, "404", "管理者以外が呼んだ(存在しない URL と同じ応答)・対象が見つからない", ref);
                }
                // エラー応答(400・409 など)は、いつもエラーの JSON の形にする。
                // springdoc は、説明だけ書いたエラー応答にも、メソッドの戻り値の形(成功のときの形)を入れてしまうので、上書きする
                operation.getResponses().forEach((status, response) -> {
                    if (status.startsWith("4") || status.startsWith("5")) {
                        response.setContent(new Content().addMediaType("application/json", new MediaType().schema(ref)));
                    }
                });
            }));
            stableOrder(openApi); // 必ず最後(上で足したエラーの形 ApiError も並べるため)
        };
    }

    /**
     * 並び順を決まった順にする。何も指定しないと、API の並び順が、Spring が部品を見つけた順(環境で変わりうる)になる。
     * docs/openapi.yaml をどの PC で作っても同じ内容にするため(OpenApiDocsTest が、このファイルと一致するかを確かめる)。
     * 並べるのは、URL(paths)とデータの形(schemas)の名前順。各データの項目は、コードに書いた順のまま
     */
    private static void stableOrder(OpenAPI openApi) {
        Paths sorted = new Paths();
        new TreeMap<>(openApi.getPaths()).forEach(sorted::addPathItem);
        openApi.setPaths(sorted);
        openApi.getComponents().setSchemas(new TreeMap<>(openApi.getComponents().getSchemas()));
    }

    private static void add(Operation operation, String status, String description, Schema<?> schema) {
        if (operation.getResponses().containsKey(status)) {
            return; // コントローラーで、その API 向けに説明を書いてあるものを優先する
        }
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
