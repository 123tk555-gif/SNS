package com.example.sns.hashtag;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ハッシュタグの保存と集計(機能定義書 3.3・3.9・3.10)。 */
@Service
@Transactional
public class HashtagService {

    /** トレンドを数える期間 */
    public static final Duration TREND_PERIOD = Duration.ofHours(24);

    /** タグ名と、使われた投稿の数 */
    public record TagCount(String name, long postCount) {
    }

    private final HashtagRepository hashtags;

    public HashtagService(HashtagRepository hashtags) {
        this.hashtags = hashtags;
    }

    /** 投稿のハッシュタグを付け直す(作成・編集のとき)。本文から取り出したタグだけが残る */
    public void tag(long postId, String text) {
        hashtags.unlinkAll(postId);
        for (String name : Hashtags.extract(text)) {
            hashtags.insertIfAbsent(name);
            hashtags.link(postId, name);
        }
    }

    /** 投稿を削除したとき、ハッシュタグとの対応を外す */
    public void untag(long postId) {
        hashtags.unlinkAll(postId);
    }

    /** トレンドのハッシュタグ(直近 24 時間。上位 limit 件) */
    @Transactional(readOnly = true)
    public List<TagCount> trending(int limit) {
        return hashtags.trending(Instant.now().minus(TREND_PERIOD), limit).stream()
                .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /** ハッシュタグの検索(先頭の「#」は無視し、全角・半角と大文字・小文字をそろえる) */
    @Transactional(readOnly = true)
    public List<TagCount> search(String query, int limit) {
        String word = Hashtags.normalize(query.strip().replaceFirst("^[#＃]", ""));
        if (word.isEmpty()) {
            return List.of();
        }
        String escaped = escapeLike(word);
        return hashtags.search("%" + escaped + "%", escaped + "%", limit).stream()
                .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /** LIKE の中で特別な意味を持つ文字(% と _)を、ただの文字として扱う */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
