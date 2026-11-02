package com.example.sns.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MuteRepository extends JpaRepository<Mute, Long> {

    /** ミュートする。すでにミュート済みなら何もしない */
    @Modifying
    @Query(value = """
            insert into mutes (from_user_id, to_user_id, created_at) values (:muterId, :mutedId, now())
            on conflict (from_user_id, to_user_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("muterId") long muterId, @Param("mutedId") long mutedId);

    @Modifying
    @Query("delete from Mute m where m.muter.id = :muterId and m.muted.id = :mutedId")
    int deleteOne(@Param("muterId") long muterId, @Param("mutedId") long mutedId);

    @Query("select count(m) > 0 from Mute m where m.muter.id = :muterId and m.muted.id = :mutedId")
    boolean exists(@Param("muterId") long muterId, @Param("mutedId") long mutedId);

    /** userId をミュートしている人 */
    @Query("select m.muter.id from Mute m where m.muted.id = :userId")
    List<Long> muterIds(@Param("userId") long userId);

    /** ミュートした人の一覧(ミュートした新しい順)。相手のユーザーは同じ SQL で取る */
    @Query("select m from Mute m join fetch m.muted where m.muter.id = :userId order by m.id desc")
    List<Mute> findByMuter(@Param("userId") long userId);
}
