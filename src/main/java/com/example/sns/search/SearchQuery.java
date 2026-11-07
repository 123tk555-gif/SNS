package com.example.sns.search;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 検索の言葉の決まり(機能定義書 3.10)。前後の空白を取り除いて 1〜100 文字。空白(全角・半角)で区切って、最大 10 語 */
final class SearchQuery {

    static final int MAX_QUERY = 100;
    /** 一度に探せる言葉の数(多すぎる言葉で、データベースに重い問い合わせをさせないため) */
    static final int MAX_WORDS = 10;
    static final String EMPTY_QUERY = "検索する言葉を入力してください";
    static final String QUERY_TOO_LONG = "100文字以内で入力してください";

    private SearchQuery() {
    }

    /** 前後の空白を取り除いた言葉。空か 100 文字を超えるときは 400(文字数は、絵文字も 1 文字として数える) */
    static String valid(String q) {
        String query = q.strip();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EMPTY_QUERY);
        }
        if (query.codePointCount(0, query.length()) > MAX_QUERY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, QUERY_TOO_LONG);
        }
        return query;
    }

    /** 空白(全角・半角)で区切った言葉。同じ言葉は 1 つにし、最初の 10 語まで */
    static List<String> words(String q) {
        return Arrays.stream(valid(q).split("[\\s　]+")).distinct().limit(MAX_WORDS).toList();
    }
}
