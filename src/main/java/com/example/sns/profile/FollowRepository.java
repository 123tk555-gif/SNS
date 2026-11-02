package com.example.sns.profile;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FollowRepository extends JpaRepository<Follow, Long> {

    /** フォローする。すでにフォロー済みなら何もしない(同時に 2 回押されても、エラーにせず 1 件だけ残る)。登録した件数(0 か 1)を返す */
    @Modifying
    @Query(value = """
            insert into follows (from_user_id, to_user_id, created_at) values (:followerId, :followeeId, now())
            on conflict (from_user_id, to_user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    int deleteOne(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    /** フォローしている人の数(利用停止中のユーザーは数えない) */
    @Query("select count(f) from Follow f where f.follower.id = :userId and f.followee.suspended = false")
    long countFollowing(@Param("userId") long userId);

    /** フォロワーの数(利用停止中のユーザーは数えない) */
    @Query("select count(f) from Follow f where f.followee.id = :userId and f.follower.suspended = false")
    long countFollowers(@Param("userId") long userId);

    /** その人がフォローしている人の一覧(フォローした新しい順)。相手のユーザーは同じ SQL で取る */
    @Query("""
            select f from Follow f join fetch f.followee u
            where f.follower.id = :userId and u.suspended = false and f.id < :before
            order by f.id desc
            """)
    List<Follow> findFollowing(@Param("userId") long userId, @Param("before") long before, Pageable pageable);

    /** その人のフォロワーの一覧(フォローされた新しい順)。相手のユーザーは同じ SQL で取る */
    @Query("""
            select f from Follow f join fetch f.follower u
            where f.followee.id = :userId and u.suspended = false and f.id < :before
            order by f.id desc
            """)
    List<Follow> findFollowers(@Param("userId") long userId, @Param("before") long before, Pageable pageable);

    /** その人をフォローしている人の ID */
    @Query("select f.follower.id from Follow f where f.followee.id = :userId")
    List<Long> followerIds(@Param("userId") long userId);

    /** userIds のうち、見る人がフォローしている人(N+1 にしないため、まとめて 1 回で取る) */
    @Query("select f.followee.id from Follow f where f.follower.id = :viewerId and f.followee.id in :userIds")
    List<Long> followedBy(@Param("viewerId") long viewerId, @Param("userIds") List<Long> userIds);

    /** userIds のうち、見る人をフォローしている人(N+1 にしないため、まとめて 1 回で取る) */
    @Query("select f.follower.id from Follow f where f.followee.id = :viewerId and f.follower.id in :userIds")
    List<Long> following(@Param("viewerId") long viewerId, @Param("userIds") List<Long> userIds);
}
