-- 負荷テスト用のデータ。テストデータ(seed)の上に足す。
-- ユーザー 30 人(load01〜load30@example.com、パスワードは seed と同じ password123)、投稿 5,000 件、いいね、フォロー、ハッシュタグ

insert into users (bio, created_at, display_name, email, email_verified, failed_login_count,
                   handle, handle_lower, password_hash, role, suspended, updated_at)
select '負荷テスト用のユーザー', now(), '負荷テスト' || lpad(i::text, 2, '0'), 'load' || lpad(i::text, 2, '0') || '@example.com',
       true, 0, 'load' || lpad(i::text, 2, '0'), 'load' || lpad(i::text, 2, '0'),
       (select password_hash from users where handle = 'taro_dev'), 'USER', false, now()
from generate_series(1, 30) as i;

-- フォロー: 負荷テストのユーザーは、ほかのユーザーのおよそ 3 分の 1 をフォローする
insert into follows (from_user_id, to_user_id, created_at)
select a.id, b.id, now()
from users a join users b on a.id <> b.id
where a.handle like 'load%' and (a.id + b.id) % 3 = 0;

-- 投稿 5,000 件(1 分おき。ID が大きいほど新しい)。作者は全ユーザーから順番に
with author as (select array_agg(id order by id) as ids from users)
insert into posts (author_id, text, hidden, created_at)
select author.ids[1 + i % array_length(author.ids, 1)],
       '負荷テストの投稿 ' || i || case when i % 5 = 0 then ' #負荷テスト' when i % 7 = 0 then ' #k6' else '' end,
       false, now() - make_interval(mins => 5001 - i)
from generate_series(1, 5000) as i, author;

-- ハッシュタグ(トレンド・タグの検索に出るように)
insert into hashtags (name) values ('負荷テスト'), ('k6') on conflict do nothing;
insert into post_hashtags (post_id, hashtag_id, created_at)
select p.id, h.id, p.created_at
from posts p join hashtags h
  on (h.name = '負荷テスト' and p.text like '%#負荷テスト') or (h.name = 'k6' and p.text like '%#k6')
where p.text like '負荷テストの投稿%';

-- いいね: 投稿と人の組み合わせの、およそ 10 分の 1
insert into likes (user_id, post_id, created_at)
select u.id, p.id, now()
from users u cross join posts p
where p.text like '負荷テストの投稿%' and (u.id * 7 + p.id) % 10 = 0;

analyze;
