package com.example.sns.hashtag;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本文からハッシュタグを取り出すルール(機能定義書 3.3)。画面のリンク化(frontend の RichText)と同じルールにする。
 * 「#」の後ろに、文字(漢字・かな・英字など)・数字・「_」・「ー」が 1〜50 文字続くもの。
 * 数字だけのものと、「#」の前の文字が英数字のもの(例: abc#tag)は対象外。
 * 大文字・小文字と、全角・半角の英数字はそろえて(小文字の半角にして)同じタグとして扱う。
 */
public final class Hashtags {

    private static final Pattern TAG = Pattern.compile("(?<![A-Za-z0-9])#([\\p{L}\\p{N}_ー]{1,50})");
    private static final Pattern DIGITS = Pattern.compile("\\p{N}+");

    private Hashtags() {
    }

    /** 本文のハッシュタグを、出てきた順に重ならないように(そろえた名前で)取り出す */
    public static Set<String> extract(String text) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!DIGITS.matcher(raw).matches()) {
                tags.add(normalize(raw));
            }
        }
        return tags;
    }

    /** 全角・半角の英数字をそろえ(NFKC)、小文字にする */
    public static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
