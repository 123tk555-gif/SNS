// API 仕様書(Swagger UI)の確認。ログインなしで開けること、画面のエラー(CSP 違反を含む)がないこと、
// 日本語の説明が出ること、ログインしたあとに「Try it out」で実際に API を呼べること(CSRF のヘッダーが自動で付くこと)。
// Swagger UI はサーバー(8080)が配信する。開発用の画面(5173)で流すときも、Swagger は 8080 で開く
import { chromium } from 'playwright-core';
import { out } from '../lib.mjs';

const base = process.argv[2] ?? 'http://localhost:5173';
const swagger = process.env.SWAGGER_BASE ?? (base.includes(':5173') ? 'http://localhost:8080' : base);
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const browser = await chromium.launch({ channel: 'msedge' });
const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 }, ignoreHTTPSErrors: true })).newPage();
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => {
  // 「ログインしていない」の 401 などの Failed to load resource は、仕様どおり。CSP 違反("Refused to …")は不具合
  if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(m.text());
});

// ---- ログインなしで開ける ----
await page.goto(`${swagger}/swagger-ui.html`);
await page.waitForSelector('.swagger-ui .info .title');
check('ログインなしで Swagger UI が開く', page.url().includes('/swagger-ui/'), page.url().replace(swagger, ''));
check('タイトルが日本語', (await page.textContent('.swagger-ui .info .title')).includes('API 仕様書'));
check('概要に「試し方」がある', (await page.textContent('.swagger-ui .info')).includes('Try it out'));
const tags = await page.locator('.opblock-tag').count();
check('タグ(見出し)が 11 個', tags === 11, `${tags}個`);
const tagNames = await page.locator('.opblock-tag').evaluateAll((els) => els.map((e) => e.getAttribute('data-tag')));
check('タグの並びが仕様どおり(アカウントが最初、管理者が最後)', tagNames[0] === 'アカウント' && tagNames.at(-1) === '管理者(投稿)', tagNames.slice(0, 2).join(' / '));

// すべてのタグを開いて、API の数を数える(開くまで中身は描かれない)
for (const tag of await page.locator('.opblock-tag').all()) await tag.click();
await page.waitForTimeout(300);
const ops = await page.locator('.opblock').count();
check('API が 53 個並ぶ', ops === 53, `${ops}個`);
check('各 API に日本語の見出しがある', (await page.locator('.opblock-summary-description').allInnerTexts()).every((t) => /[぀-ヿ一-鿿]/.test(t)));
await page.screenshot({ path: `${out}/m11-1-swagger.png` });

// データの形(スキーマ)が日本語で出ている
const models = await page.locator('section.models').count();
check('データの形(Schemas)の一覧がある', models > 0);

// ---- ログインしてから、実際に API を呼ぶ(CSRF のヘッダーが自動で付く) ----
// アプリにログインした状態にする(同じアドレスなので、Swagger UI にも Cookie が付く)
const loginStatus = await page.evaluate(async () => {
  await fetch('/api/auth/config'); // CSRF 対策の Cookie を受け取る
  const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
  const r = await fetch('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    body: JSON.stringify({ email: 'taro@example.com', password: 'password123' }),
  });
  return r.status;
});
check('(準備)アプリにログインした', loginStatus === 200, `${loginStatus}`);

async function tryIt(path, method) {
  const block = page.locator(`.opblock-${method}:has(.opblock-summary-path[data-path="${path}"])`).first();
  if (!(await block.locator('.try-out__btn').count())) await block.locator('.opblock-summary').click();
  await block.locator('.try-out__btn').click();
  await block.locator('.execute').click();
  await block.locator('.live-responses-table td.response-col_status:not(.col_header)').waitFor({ timeout: 8000 });
  return { block, status: (await block.locator('.live-responses-table td.response-col_status:not(.col_header)').first().innerText()).trim() };
}

const me = await tryIt('/api/auth/me', 'get');
check('GET /api/auth/me を Try it out → 200', me.status.startsWith('200'), me.status);
check('ログインしているユーザーが返る', (await me.block.locator('.live-responses-table .microlight').first().innerText()).includes('taro_dev'));

const read = await tryIt('/api/notifications/read', 'post');
check('POST /api/notifications/read(CSRF が必要な API)→ 200(403 にならない)', read.status.startsWith('200'), read.status);
await page.screenshot({ path: `${out}/m11-2-tryit.png` });

// 一覧の引数の説明が日本語で出ている
const timeline = page.locator('.opblock-get:has(.opblock-summary-path[data-path="/api/timeline"])').first();
if (!(await timeline.locator('.parameters-col_description').count())) await timeline.locator('.opblock-summary').click();
const paramText = await timeline.locator('.parameters').innerText();
check('引数 before・after・limit・tab に日本語の説明', ['before', 'after', 'limit', 'tab'].every((p) => paramText.includes(p)) && paramText.includes('続きの読み込み'));

// 仕様のファイルも、ログインなしで取れる
const yamlResponse = await (await browser.newContext()).request.get(`${swagger}/v3/api-docs.yaml`);
check('/v3/api-docs.yaml をログインなしで取れる', yamlResponse.status() === 200 && (await yamlResponse.text()).startsWith('openapi: 3.1'));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
