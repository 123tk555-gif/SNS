package com.example.sns.hashtag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ハッシュタグを取り出すルール(機能定義書 3.3)。 */
class HashtagsTest {

    @Test
    void 日本語_英数字_アンダースコア_長音のタグを取り出す() {
        assertThat(Hashtags.extract("今日は #SpringBoot と #カフェ巡り、#写真_練習 #ラーメン です"))
                .containsExactly("springboot", "カフェ巡り", "写真_練習", "ラーメン");
    }

    @Test
    void 全角の英数字と大文字はそろえて同じタグにする() {
        assertThat(Hashtags.extract("#ＳｐｒｉｎｇＢｏｏｔ #springboot #SPRINGBOOT")).containsExactly("springboot");
    }

    @Test
    void 数字だけのタグと_前が英数字のタグは対象外() {
        assertThat(Hashtags.extract("#123 と abc#tag と #2026年")).containsExactly("2026年");
    }

    @Test
    void 長さは50文字まで() {
        assertThat(Hashtags.extract("#" + "あ".repeat(60))).containsExactly("あ".repeat(50));
    }
}
