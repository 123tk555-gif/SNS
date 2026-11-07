package com.example.sns.common;

/**
 * LIKE 検索に渡す「言葉」を作る。LIKE では % は「何文字でも」、_ は「どれか 1 文字」という意味の記号なので、
 * 利用者が入力した % と _ は、前に \ を付けて、ただの文字として探す(SQL 側で escape '\' を付ける)。
 * 打ち消しに使う \ そのものも、先に打ち消す。
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** % と _ と \ を、ただの文字として扱う形にする */
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 部分一致(どこかに含む) */
    public static String contains(String word) {
        return "%" + escape(word) + "%";
    }

    /** 前方一致(その言葉で始まる) */
    public static String prefix(String word) {
        return escape(word) + "%";
    }
}
