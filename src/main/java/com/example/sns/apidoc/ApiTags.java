package com.example.sns.apidoc;

/**
 * API 仕様書(Swagger UI)のタグ(画面の見出し)の名前。コントローラーの @Tag(name = ApiTags.…) と、
 * OpenApiConfig のタグの説明・並び順で、同じ文字を使うための定数。
 */
public final class ApiTags {

    public static final String AUTH = "アカウント";
    public static final String USER = "ユーザー";
    public static final String SETTINGS = "プロフィール編集";
    public static final String POST = "投稿・タイムライン";
    public static final String REACTION = "いいね・ブックマーク";
    public static final String NOTIFICATION = "通知";
    public static final String SEARCH = "検索・トレンド";
    public static final String STREAM = "リアルタイム配信(SSE)";
    public static final String MEDIA = "画像";
    public static final String ADMIN_USER = "管理者(ユーザー)";
    public static final String ADMIN_POST = "管理者(投稿)";

    private ApiTags() {
    }
}
