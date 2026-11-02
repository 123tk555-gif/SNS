// テストで作ったデータ(投稿・フォロー・ブロック・通知・テスト用ユーザー)を、開発用データベースから消す。
// 残すもの: ユーザー(seed の 5 人とふだん使っているアカウント)。テスト用のユーザー(ユーザーID が e2e_ で始まる)は消す。
// テストで変えたユーザーの状態(利用停止・ログインの失敗によるロック)は元に戻す。
// 使い方: node cleanup.mjs   (Docker のコンテナ sns-db が動いていること)
import { execFileSync } from 'node:child_process';

const sql = `
delete from notifications; delete from blocks; delete from mutes; delete from follows;
delete from likes; delete from bookmarks; delete from post_hashtags; delete from post_images;
delete from posts; delete from hashtags;
delete from email_tokens where user_id in (select id from users where handle like 'e2e\\_%');
delete from users where handle like 'e2e\\_%';
update users set suspended = false where suspended;
update users set failed_login_count = 0, locked_until = null where failed_login_count > 0 or locked_until is not null;
`;

export function cleanup() {
  execFileSync('docker', ['exec', 'sns-db', 'psql', '-U', 'sns', '-d', 'sns', '-q', '-c', sql], { stdio: 'inherit' });
}

if (import.meta.url === `file:///${process.argv[1].replaceAll('\\', '/')}`) {
  cleanup();
  console.log('テストデータを消しました');
}
