package com.example.sns.profile;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ブロック・ミュートの関係を確かめる(機能定義書 1.6 の V-04・V-05、F-BLOCK-01, 02、F-NOTIF-06)。
 * 投稿・いいね・通知・プロフィールのどこからでも使えるよう、ほかのサービスには頼らない。
 *
 * 一覧の問い合わせ(JPQL)では、見る人と投稿者(別名 a)の関係を、下の定数の条件で絞り込む。
 */
@Component
@Transactional(readOnly = true)
public class Relations {

    /**
     * V-04・V-05(JPQL): 投稿者(a)と見る人(:viewerId)の間に、どちら向きのブロックもなく、見る人が投稿者をミュートしていない
     * (前に「and 」を書いてつなぐ。テキストブロックは行末の空白を消すので、定数の先頭に空白を入れてある)
     */
    public static final String VISIBLE_TO_VIEWER = " " + """
            not exists (select 1 from Block blk
                        where (blk.blocker.id = :viewerId and blk.blocked.id = a.id)
                           or (blk.blocker.id = a.id and blk.blocked.id = :viewerId))
            and not exists (select 1 from Mute mt where mt.muter.id = :viewerId and mt.muted.id = a.id)
            """;

    /** V-04 だけ(JPQL): ブロックの関係がない(ミュートは見る人の画面の都合なので、詳細や人の一覧では除かない) */
    public static final String NOT_BLOCKED = " " + """
            not exists (select 1 from Block blk
                        where (blk.blocker.id = :viewerId and blk.blocked.id = a.id)
                           or (blk.blocker.id = a.id and blk.blocked.id = :viewerId))
            """;

    /** V-04・V-05(SQL): 投稿者(u)と見る人(:viewerId)の関係。ネイティブの問い合わせ用 */
    public static final String VISIBLE_TO_VIEWER_SQL = " " + """
            not exists (select 1 from blocks blk
                        where (blk.from_user_id = :viewerId and blk.to_user_id = u.id)
                           or (blk.from_user_id = u.id and blk.to_user_id = :viewerId))
            and not exists (select 1 from mutes mt where mt.from_user_id = :viewerId and mt.to_user_id = u.id)
            """;

    private final BlockRepository blocks;
    private final MuteRepository mutes;

    Relations(BlockRepository blocks, MuteRepository mutes) {
        this.blocks = blocks;
        this.mutes = mutes;
    }

    /** a と b の間に、どちら向きでもブロックがあるか */
    public boolean blockedEitherWay(long a, long b) {
        return blocks.existsEitherWay(a, b);
    }

    public boolean blocks(long blockerId, long blockedId) {
        return blocks.exists(blockerId, blockedId);
    }

    public boolean mutes(long muterId, long mutedId) {
        return mutes.exists(muterId, mutedId);
    }

    /**
     * actorId(操作した人)から recipientId(受け取る人)へ通知を送らないか(F-NOTIF-06):
     * どちら向きでもブロックの関係があるか、受け取る人が操作した人をミュートしている
     */
    public boolean silenced(long actorId, long recipientId) {
        return blocks.existsEitherWay(actorId, recipientId) || mutes.exists(recipientId, actorId);
    }

    /** authorId の投稿を見ない人(ブロックの関係にある人と、authorId をミュートしている人)。リアルタイム配信で使う */
    public Set<Long> hiddenFrom(long authorId) {
        Set<Long> ids = new HashSet<>(blocks.relatedIds(authorId));
        ids.addAll(mutes.muterIds(authorId));
        return ids;
    }
}
