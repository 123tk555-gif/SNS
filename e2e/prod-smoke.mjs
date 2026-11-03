// 公開用の構成(compose.prod.yaml)の動作確認。HTTPS の入り口(Caddy)を通して、登録から投稿・リアルタイム反映までを確かめる。
// 手元で試す手順は README の「公開用の構成を手元で試す」を見る。
// 使い方: BASE_URL=https://localhost MAILPIT_URL=http://127.0.0.1:18025 node prod-smoke.mjs
import { chromium } from 'playwright-core';
import { fixture } from './lib.mjs';

const base = process.env.BASE_URL ?? 'https://localhost';
const mailpit = process.env.MAILPIT_URL ?? 'http://127.0.0.1:18025';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = String(Date.now()).slice(-6);
const browser = await chromium.launch({ channel: 'msedge' });

async function token(to) {
  for (let i = 0; i < 30; i += 1) {
    const list = await (await fetch(`${mailpit}/api/v1/messages`)).json();
    const message = list.messages.find((m) => m.To.some((t) => t.Address === to) && m.Subject.includes('確認'));
    if (message) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${message.ID}`)).json();
      return { token: full.Text.match(/token=([A-Za-z0-9_-]+)/)[1], text: full.Text };
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error('メールが届きません');
}

async function signUp(name) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => errors.push(`${name}: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(`${name}: ${m.text()}`); });
  const email = `${name}-${stamp}@example.com`;
  await page.goto(`${base}/signup`);
  await page.fill('#signup-email', email);
  await page.fill('#signup-password', 'password123');
  await page.click('button:has-text("登録する")');
  await page.waitForSelector('h1:has-text("確認メールを送りました")');
  const mail = await token(email);
  if (name === 'smoke_a') check('メールのリンクが公開の URL(https)になっている', mail.text.includes(`${base}/verify-email?token=`), mail.text.match(/https?:\/\/\S+/)?.[0]?.slice(0, 40));
  await page.goto(`${base}/verify-email?token=${mail.token}`);
  await page.waitForSelector('h1:has-text("プロフィールを設定しましょう")');
  await page.fill('#onb-handle', `e2e_${name}${stamp}`.slice(0, 15));
  await page.waitForSelector('text=使えます');
  await page.fill('#onb-name', name);
  await page.click('button:has-text("はじめる")');
  await page.waitForSelector('h1:has-text("ホーム")');
  return { page, context };
}

const a = await signUp('smoke_a');
const b = await signUp('smoke_b');
check('登録・確認メール・初期設定・ホーム(HTTPS)', true);

// Cookie の属性(NF-SEC-04)
const cookies = await a.context.cookies();
const session = cookies.find((c) => c.name === 'SESSION');
check('SESSION は HttpOnly・Secure・SameSite=Lax', session?.httpOnly === true && session?.secure === true && session?.sameSite === 'Lax', JSON.stringify(session && { h: session.httpOnly, s: session.secure, ss: session.sameSite }));

// ヘッダー
const headers = await a.page.evaluate(async () => Object.fromEntries((await fetch('/api/auth/config')).headers.entries()));
check('CSP・HSTS・nosniff が付いている', !!headers['content-security-policy'] && !!headers['strict-transport-security'] && headers['x-content-type-options'] === 'nosniff');

// 投稿(画像つき)→ もう一方の画面に、再読み込みなしで届く(SSE が Caddy を通る)
await a.page.fill('#text-home', `公開構成の確認 ${stamp} #smoke${stamp}`);
await a.page.setInputFiles('form.composer input[type="file"]', fixture('smoke.png', 200, 120));
await a.page.waitForSelector('form.composer .thumb');
await a.page.fill('#alt-home-0', '確認用の画像');
await a.page.click('form.composer button[type="submit"]');
await a.page.waitForSelector(`article.card:has-text("公開構成の確認 ${stamp}")`);
check('画像つきの投稿ができる', await a.page.locator('article.card .imgs img').first().isVisible());
await b.page.waitForSelector(`article.card:has-text("公開構成の確認 ${stamp}")`, { timeout: 10000 });
check('別のブラウザに、再読み込みなしで届く(SSE)', true);
// 画像は読み込みに少し時間がかかることがあるので、最大 5 秒待つ。あわせて、/media の応答も直接確かめる
const loaded = await a.page.waitForFunction(() => [...document.querySelectorAll('article.card .imgs img')].every((i) => i.complete && i.naturalWidth > 0), null, { timeout: 5000 }).then(() => true, () => false);
const media = await a.page.evaluate(async () => { const url = document.querySelector('article.card .imgs img')?.getAttribute('src'); const r = await fetch(url); return `${r.status} ${r.headers.get('content-type')} ${(await r.arrayBuffer()).byteLength}bytes`; });
check('画像(/media)が表示される', loaded, media);

// 再読み込みしてもログインが続く(セッションは公開用のデータベース)
await a.page.reload();
await a.page.waitForSelector('h1:has-text("ホーム")');
check('再読み込みしてもログインが続く', true);

// 管理者の API は一般ユーザーには 404
const admin = await a.page.evaluate(async () => (await fetch('/api/admin/users')).status);
check('一般ユーザーは管理者 API が 404', admin === 404);

// API 仕様書(Swagger UI)。HTTPS の入り口を通しても開け、画面のエラー(CSP 違反を含む)がなく、ログイン中なら Try it out で API を呼べる
await a.page.goto(`${base}/swagger-ui.html`);
await a.page.waitForSelector('.swagger-ui .info .title');
check('API 仕様書(Swagger UI)が HTTPS で開く', (await a.page.textContent('.swagger-ui .info .title')).includes('API 仕様書'));
const authTag = a.page.locator('.opblock-tag[data-tag="アカウント"]');
await authTag.click();
const meBlock = a.page.locator('.opblock-get:has(.opblock-summary-path[data-path="/api/auth/me"])').first();
await meBlock.locator('.opblock-summary').click();
await meBlock.locator('.try-out__btn').click();
await meBlock.locator('.execute').click();
await meBlock.locator('.live-responses-table td.response-col_status:not(.col_header)').waitFor({ timeout: 8000 });
check('Swagger UI の Try it out で /api/auth/me → 200', (await meBlock.locator('.live-responses-table td.response-col_status:not(.col_header)').first().innerText()).startsWith('200'));
const anonymous = await browser.newContext({ ignoreHTTPSErrors: true });
const docs = await anonymous.request.get(`${base}/v3/api-docs`);
check('/v3/api-docs をログインなしで取れる', docs.status() === 200 && (await docs.json()).openapi?.startsWith('3.'), `${docs.status()}`);

// HTTP で開くと HTTPS に移る
const http = await fetch(base.replace('https://', 'http://'), { redirect: 'manual' });
check('HTTP は HTTPS へ転送される', http.status >= 300 && http.status < 400 && (http.headers.get('location') ?? '').startsWith('https://'));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
process.exit(results.some((r) => r.startsWith('NG')) || errors.length ? 1 : 0);
