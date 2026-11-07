package com.example.sns.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 検索の言葉の決まり(機能定義書 3.10)の単体テスト。前後の空白を取り除いて 1〜100 文字、空白で区切って最大 10 語 */
class SearchQueryTest {

    @Test
    void 前後の空白を取り除く() {
        assertThat(SearchQuery.valid("  カフェ  ")).isEqualTo("カフェ");
    }

    @Test
    void 空と_101文字以上は_400で断る() {
        assertBadRequest("", SearchQuery.EMPTY_QUERY);
        assertBadRequest(" 　 ", SearchQuery.EMPTY_QUERY); // 全角の空白だけ
        assertBadRequest("あ".repeat(101), SearchQuery.QUERY_TOO_LONG);
        assertThat(SearchQuery.valid("あ".repeat(100))).hasSize(100);
    }

    @Test
    void 文字数は絵文字も1文字として数える() {
        assertThat(SearchQuery.valid("😀".repeat(100))).isNotEmpty(); // Java の length() では 200
        assertBadRequest("😀".repeat(101), SearchQuery.QUERY_TOO_LONG);
    }

    @Test
    void 半角と全角の空白で区切り_同じ言葉は1つにする() {
        assertThat(SearchQuery.words("カフェ 勉強　カフェ  Java")).containsExactly("カフェ", "勉強", "Java");
    }

    @Test
    void 言葉は最初の10語まで() {
        assertThat(SearchQuery.words("a b c d e f g h i j k l")).hasSize(SearchQuery.MAX_WORDS)
                .startsWith("a").endsWith("j");
    }

    private static void assertBadRequest(String query, String message) {
        assertThatThrownBy(() -> SearchQuery.valid(query))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
