package com.example.sns.post;

import com.example.sns.apidoc.ApiTags;
import com.example.sns.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 投稿とタイムラインの API(機能定義書 3.3・3.5)。 */
@RestController
@Tag(name = ApiTags.POST)
class PostController {

    @Schema(description = "投稿の編集に送る内容")
    record EditRequest(
            @Schema(description = "新しい本文(1000 文字以内。画像がない投稿は、空にできない)", example = "書き直しました #SpringBoot")
            String text) {
    }

    /** 投稿の作成の、multipart/form-data の項目(API 仕様書の説明用。実際の受け取りは create の引数で行う) */
    @Schema(name = "PostForm", description = "投稿・返信の作成に送る内容(`multipart/form-data`)")
    record PostForm(
            @Schema(description = "本文(1000 文字以内)。画像がないときは必須", example = "新しいカフェを見つけました #カフェ巡り")
            String text,
            @Schema(description = "返信先の投稿の ID。返信のときだけ送る", example = "123", nullable = true)
            Long replyToId,
            @ArraySchema(arraySchema = @Schema(description = "画像ファイル(0〜10 枚。1 枚 5MB まで。JPEG・PNG・WebP・GIF。形式は拡張子ではなく中身で確かめる)"),
                    schema = @Schema(type = "string", format = "binary"), maxItems = 10)
            List<String> images,
            @ArraySchema(arraySchema = @Schema(description = "画像ごとの代替テキスト(1 つ 1000 文字以内。空でもよい)。`images` と同じ順番・同じ数で送る。"
                    + "「,」を含んでも分かれず、送られたとおりに受け取る"), schema = @Schema(type = "string"))
            List<String> alts) {
    }

    private final PostService service;

    PostController(PostService service) {
        this.service = service;
    }

    /**
     * 投稿・返信の作成。multipart/form-data で text・images(複数)・alts(画像と同じ順番・同じ数)・
     * replyToId(返信のときだけ)を送る。alts は「,」を含んでも分かれないよう、送られたとおりの値をそのまま受け取る。
     */
    @PostMapping(value = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "投稿・返信の作成",
            description = "本文と画像(最大 10 枚)で投稿を作る。`replyToId` を付けると、その投稿への返信になる(返信はタイムラインには出ない)。"
                    + "本文からハッシュタグ・メンションを取り出して保存し、返信先の投稿者とメンションの相手(最大 10 人)に通知を作る。"
                    + "返信でない投稿は、リアルタイム配信(SSE)で、接続中の他のユーザーに届く。"
                    + "画像・投稿・ハッシュタグは、まとめて保存する(どれかが失敗したら全部取り消し、保存した画像も消す)",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PostForm.class))))
    @ApiResponse(responseCode = "201", description = "作成した。作った投稿を返す(`Location` ヘッダーに、その投稿の URL)")
    @ApiResponse(responseCode = "400", description = "本文も画像もない・本文が 1000 文字を超える・画像が 10 枚を超える・画像でないファイル・代替テキストが 1000 文字を超える")
    @ApiResponse(responseCode = "403", description = "返信先の投稿者との間にブロックの関係がある、または初期設定が済んでいない")
    @ApiResponse(responseCode = "404", description = "返信先の投稿が見つからない(削除・非表示・ブロックで見えないものを含む)")
    @ApiResponse(responseCode = "413", description = "画像が 5MB を超える")
    ResponseEntity<PostResponse> create(@AuthenticationPrincipal LoginUser me,
            @Parameter(hidden = true) @RequestParam(defaultValue = "") String text,
            @Parameter(hidden = true) @RequestParam(required = false) Long replyToId,
            @Parameter(hidden = true) @RequestPart(name = "images", required = false) List<MultipartFile> images,
            HttpServletRequest request) {
        String[] alts = request.getParameterValues("alts");
        PostResponse created = service.create(me.id(), text, images, alts == null ? List.of() : Arrays.asList(alts),
                replyToId);
        return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
    }

    /** 返信の一覧(古い順)。続きは nextCursor を after に渡す */
    @GetMapping("/api/posts/{id}/replies")
    @Operation(summary = "返信の一覧",
            description = "ある投稿への返信を、古い順(会話の流れの順)に返す。続きは、前の結果の `nextCursor` を `after` に渡して取る。"
                    + "削除・非表示の返信と、ブロック・ミュートの関係にある人の返信は出ない")
    @ApiResponse(responseCode = "200", description = "返信の一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "404", description = "元の投稿が見つからない(削除・非表示・ブロックで見えないものを含む)")
    PostService.Page replies(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "元の投稿の ID", example = "123") @PathVariable long id,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`(この ID より新しい返信を取る)", example = "130")
            @RequestParam(required = false) Long after,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        return service.replies(id, me.id(), after, limit);
    }

    @GetMapping("/api/posts/{id}")
    @Operation(summary = "投稿の取得",
            description = "投稿 1 件を返す。削除された・管理者に非表示にされた・投稿者が利用停止中の投稿は、"
                    + "`unavailable` が `true` で、ID 以外が空の形で返す(返信先として「削除されました」と表示するため)。"
                    + "ミュートした人の投稿も、自分で開いたものなので返す")
    @ApiResponse(responseCode = "200", description = "投稿(見られないときは `unavailable: true`)")
    @ApiResponse(responseCode = "404", description = "投稿が存在しない、またはブロックの関係にある人の投稿")
    PostResponse get(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        return service.get(id, me.id());
    }

    @PutMapping("/api/posts/{id}")
    @Operation(summary = "投稿の編集",
            description = "自分の投稿の本文を変える(画像は変えられない)。`editedAt` に今の日時を入れ、ハッシュタグを付け直す。"
                    + "編集で新しく増えたメンションの相手にだけ、通知を作る")
    @ApiResponse(responseCode = "200", description = "編集した。更新後の投稿を返す")
    @ApiResponse(responseCode = "400", description = "本文が 1000 文字を超える、または画像のない投稿の本文が空")
    @ApiResponse(responseCode = "403", description = "自分の投稿ではない")
    @ApiResponse(responseCode = "404", description = "投稿が見つからない")
    PostResponse edit(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id, @RequestBody EditRequest request) {
        return service.edit(id, me.id(), request.text());
    }

    @DeleteMapping("/api/posts/{id}")
    @Operation(summary = "投稿の削除",
            description = "自分の投稿を削除する。投稿の行は残して「削除済み」にし、本文を空にする(返信先として「この投稿は削除されました」と表示するため)。"
                    + "画像ファイル、その投稿へのいいね・ブックマーク・ハッシュタグの対応・通知は消す。"
                    + "削除したことは、リアルタイム配信(`post-deleted`)で、開いている画面に届く")
    @ApiResponse(responseCode = "204", description = "削除した")
    @ApiResponse(responseCode = "403", description = "自分の投稿ではない")
    @ApiResponse(responseCode = "404", description = "投稿が見つからない")
    ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "投稿の ID", example = "123") @PathVariable long id) {
        service.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * タイムライン。tab は all(すべて)か following(フォロー中)。
     * after を渡すと、それより新しい投稿を古い順に返す(新着の取り込み。機能定義書 3.5)
     */
    @GetMapping("/api/timeline")
    @Operation(summary = "タイムライン",
            description = "返信以外の投稿を、新しい順に返す。`tab=all` は全ユーザー、`tab=following` は自分とフォロー中のユーザーの投稿。"
                    + "削除・非表示の投稿、利用停止中のユーザーと、ブロック・ミュートの関係にある人の投稿は出ない。"
                    + "続きは、前の結果の `nextCursor` を `before` に渡して取る。\n\n"
                    + "`after` を渡すと、それより新しい投稿を**古い順**に最大 50 件返す(リアルタイム配信が切れていた間の、新着の取り込み用)")
    @ApiResponse(responseCode = "200", description = "投稿の一覧。`nextCursor` が `null` なら、それ以上はない")
    @ApiResponse(responseCode = "400", description = "`tab` の値が正しくない")
    PostService.Page timeline(@AuthenticationPrincipal LoginUser me,
            @Parameter(description = "`all`(すべて)か `following`(フォロー中)", schema = @Schema(allowableValues = {"all", "following"}, defaultValue = "all"))
            @RequestParam(defaultValue = "all") String tab,
            @Parameter(description = "続きの読み込み。前の結果の `nextCursor`(この ID より古い投稿を取る)", example = "1234")
            @RequestParam(required = false) Long before,
            @Parameter(description = "新着の取り込み。この ID より新しい投稿を、古い順に返す(`before` より優先)", example = "1300")
            @RequestParam(required = false) Long after,
            @Parameter(description = "件数(1〜50。省略すると 20)", example = "20") @RequestParam(required = false) Integer limit) {
        if (!tab.equals("all") && !tab.equals("following")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tab の値が正しくありません");
        }
        boolean following = tab.equals("following");
        if (after != null) {
            return service.timelineAfter(me.id(), following, after);
        }
        return service.timeline(me.id(), following, before, limit);
    }
}
