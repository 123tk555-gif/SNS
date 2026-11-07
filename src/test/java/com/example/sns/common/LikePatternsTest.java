package com.example.sns.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * LIKE 検索の「言葉」を作る部品の単体テスト(Spring も DB も使わない)。
 * LIKE では % は「何文字でも」、_ は「どれか 1 文字」という意味の記号なので、
 * 利用者が入力した % と _ は、ただの文字として探せるように打ち消す(前に \ を付ける)。
 * SQL 側では escape '\' を付けて使う。
 */
class LikePatternsTest {

    @Test
    void 記号でない文字はそのまま() {
        assertThat(LikePatterns.escape("taro")).isEqualTo("taro");
        assertThat(LikePatterns.escape("カフェ巡り")).isEqualTo("カフェ巡り");
        assertThat(LikePatterns.escape("")).isEmpty();
    }

    @Test
    void パーセントとアンダースコアは前に円記号を付けて打ち消す() {
        assertThat(LikePatterns.escape("100%")).isEqualTo("100\\%");
        assertThat(LikePatterns.escape("taro_dev")).isEqualTo("taro\\_dev");
    }

    @Test
    void 打ち消しに使う円記号そのものも打ち消す() {
        // 先に \ を打ち消さないと、「\%」のように入力されたとき、利用者の \ が % を打ち消してしまう
        assertThat(LikePatterns.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(LikePatterns.escape("\\%")).isEqualTo("\\\\\\%");
    }

    @Test
    void 部分一致と前方一致のパターン() {
        assertThat(LikePatterns.contains("a_b")).isEqualTo("%a\\_b%");
        assertThat(LikePatterns.prefix("ta%")).isEqualTo("ta\\%%");
    }
}
