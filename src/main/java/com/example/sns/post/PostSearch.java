package com.example.sns.post;

import com.example.sns.hashtag.HashtagService;
import com.example.sns.hashtag.Hashtags;
import com.example.sns.profile.Relations;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 投稿の本文の検索(F-SEARCH-01)。空白で区切った言葉を、すべて含む投稿の ID を新しい順に返す。
 * 本文はデータベース側で normalize(NFKC)と lower でそろえてから部分一致で探す
 * (全角・半角の英数字と、大文字・小文字を区別しない。日本語の文章もそのまま探せる)。
 * 言葉の数に合わせて条件を組み立てるが、言葉そのものは必ずパラメータで渡す(SQL に埋め込まない)。
 */
@Component
class PostSearch {

    private final NamedParameterJdbcTemplate jdbc;

    PostSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** viewerId とブロック・ミュートの関係にある人の投稿は出さない(F-SEARCH-05) */
    List<Long> findIds(long viewerId, List<String> words, long before, int limit) {
        StringBuilder sql = new StringBuilder("""
                select p.id from posts p join users u on u.id = p.author_id
                where p.deleted_at is null and p.hidden = false and u.suspended = false and p.id < :before
                  and """ + Relations.VISIBLE_TO_VIEWER_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("before", before)
                .addValue("limit", limit);
        for (int i = 0; i < words.size(); i++) {
            sql.append(" and lower(normalize(p.text, NFKC)) like :w").append(i).append(" escape '\\'");
            params.addValue("w" + i, "%" + HashtagService.escapeLike(Hashtags.normalize(words.get(i))) + "%");
        }
        sql.append(" order by p.id desc limit :limit");
        return jdbc.queryForList(sql.toString(), params, Long.class);
    }
}
