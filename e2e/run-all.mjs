// 画面の操作テストを、順番にすべて流す。各テストの前後でテストデータを消す。
// 前提: データベース(docker compose up -d)・サーバー(8080)・画面(5173)が動いていて、
//       seed のユーザー(taro@example.com など。パスワード password123)がいること。
// 使い方: npm test        (特定のテストだけ: node run-all.mjs m8)
import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { cleanup } from './cleanup.mjs';

const base = process.env.BASE_URL ?? 'http://localhost:5173';
const only = process.argv[2];
const flows = readdirSync(new URL('./flows/', import.meta.url)).filter((f) => f.endsWith('-flow.mjs') && (!only || f.includes(only))).sort();

try {
  const response = await fetch(`${base}/api/auth/config`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
} catch (e) {
  console.error(`${base} につながりません(${e.message})。サーバーと画面を起動してください`);
  process.exit(2);
}

let failed = 0;
for (const flow of flows) {
  cleanup();
  const run = spawnSync('node', [new URL(`./flows/${flow}`, import.meta.url).pathname.replace(/^\/(\w:)/, '$1'), base], { encoding: 'utf8' });
  const lines = run.stdout.split('\n');
  const ng = lines.filter((l) => l.startsWith('NG'));
  // 画面のエラー(console の error・例外)。ログインしていないときの 401 は仕様どおりなので数えない
  const errorStart = lines.findIndex((l) => l.startsWith('エラー:'));
  const errors = errorStart < 0 ? [] : lines.slice(errorStart + 1).filter((l) => l.trim() && !l.includes('status of 401') && !l.includes('status of 404'));
  const ok = lines.filter((l) => l.startsWith('OK')).length;
  const crashed = run.status !== 0;
  const bad = ng.length > 0 || errors.length > 0 || crashed;
  console.log(`${bad ? 'NG' : 'OK'}  ${flow}  (${ok}項目 OK${ng.length ? `, ${ng.length}項目 NG` : ''})`);
  if (bad) {
    failed++;
    for (const l of [...ng, ...errors]) console.log(`    ${l}`);
    if (crashed) console.log(run.stderr.split('\n').slice(0, 6).map((l) => `    ${l}`).join('\n'));
  }
}
cleanup();
console.log(failed ? `\n${failed} 本のテストで問題がありました` : `\n${flows.length} 本すべて成功しました`);
process.exit(failed ? 1 : 0);
