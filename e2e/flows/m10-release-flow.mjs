// 公開前の点検(M10): ふつうの操作のテストでは確かめていない、公開すると起こりがちなこと。
// HTML の埋め込み・長い文字列の折り返し・絵文字の数え方・二度押し・通信の失敗・キーボードだけの操作・
// 画像の枚数・ログインのロック・別のタブでのログアウト・URL を直接開く
import { chromium } from 'playwright-core';
import { fixture, out } from '../lib.mjs';

const base = process.argv[2] ?? 'http://localhost:5173';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = String(Date.now()).slice(-6);
const browser = await chromium.launch({ channel: 'msedge' });

async function loginAs(email, viewport = { width: 1280, height: 900 }, ctx) {
  const context = ctx ?? await browser.newContext({ viewport });
  const page = await context.newPage();
  page.on('pageerror', (e) => errors.push(`${email}: ${e.message}`));
  // alert などのダイアログが出たら、スクリプトが動いたということ
  page.on('dialog', (d) => { errors.push(`${email}: ダイアログが出た ${d.message()}`); d.dismiss(); });
  page.on('console', (m) => {
    // 開発サーバー(Vite)の自動更新の接続は、テストとは関係ない
    if (m.type() === 'error' && !m.text().includes('Failed to load resource') && !m.text().includes('WebSocket')) errors.push(`${email}: ${m.text()}`);
  });
  await page.goto(base + '/login');
  await page.fill('#login-email', email);
  await page.fill('#login-password', 'password123');
  await page.click('form button[type="submit"]');
  await page.waitForSelector('h1:has-text("ホーム")');
  return page;
}
const api = (page, method, url, body) => page.evaluate(async ({ method, url, body }) => {
  const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
  const headers = { 'X-XSRF-TOKEN': token };
  let payload;
  if (body?.form) {
    payload = new FormData();
    for (const [k, v] of Object.entries(body.form)) payload.append(k, v);
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const r = await fetch(url, { method, headers, body: payload });
  return { status: r.status, body: await r.json().catch(() => null) };
}, { method, url, body });
const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth - innerWidth);

const taro = await loginAs('taro@example.com');
const taroProfile = (await api(taro, 'GET', '/api/users/taro_dev')).body;

// 1. 本文・表示名・自己紹介に HTML を入れても、文字として出る(スクリプトは動かない)
await api(taro, 'POST', '/api/posts', { form: { text: `<img src=x onerror="window.__xss=1"><script>window.__xss=2</script> ${stamp}` } });
await api(taro, 'PUT', '/api/me/profile', { displayName: '<b onmouseover=alert(1)>太郎</b>', bio: '<script>alert(1)</script>' });
await taro.reload();
await taro.waitForSelector(`article.card:has-text("${stamp}")`);
await taro.goto(`${base}/users/taro_dev`);
await taro.waitForSelector('section.profile');
check('HTML を入れてもスクリプトは動かない', (await taro.evaluate(() => window.__xss)) === undefined);
check('HTML は文字のまま表示される', (await taro.locator('section.profile h2').innerText()).includes('<b onmouseover'));
await api(taro, 'PUT', '/api/me/profile', { displayName: taroProfile.displayName, bio: taroProfile.bio ?? '' });

// 2. 空白のない長い英数字(URL・名前・自己紹介)でも、スマホ幅で横にはみ出さない
const phone = await loginAs('hanako@example.com', { width: 375, height: 812 });
const hanakoProfile = (await api(phone, 'GET', '/api/users/hanako')).body;
const long = await api(phone, 'POST', '/api/posts', {
  form: { text: `${'A'.repeat(300)}\nhttps://example.com/${'path/'.repeat(60)}?q=${'x'.repeat(100)}\n#${'長'.repeat(50)}` },
});
await api(phone, 'PUT', '/api/me/profile', { displayName: 'W'.repeat(50), bio: 'B'.repeat(160) });
for (const path of ['/', `/posts/${long.body.id}`, '/users/hanako', '/search?q=WWW&tab=users', `/search?q=${'A'.repeat(50)}`]) {
  await phone.goto(base + path);
  await phone.waitForSelector('h1');
  await phone.waitForTimeout(500);
  const o = await overflow(phone);
  check(`長い文字列でも横にはみ出さない ${path.slice(0, 24)}`, o <= 0, o > 0 ? `${o}px` : '');
}
await phone.goto(`${base}/users/hanako`);
await phone.waitForSelector('section.profile');
check('長い表示名の見出しは 1 行で「…」', await phone.evaluate(() => {
  const h1 = document.querySelector('.topbar h1');
  return getComputedStyle(h1).textOverflow === 'ellipsis' && h1.scrollWidth > h1.clientWidth;
}));
await phone.screenshot({ path: `${out}/m10-1-long-name.png` });
await api(phone, 'PUT', '/api/me/profile', { displayName: hanakoProfile.displayName, bio: hanakoProfile.bio ?? '' });

// 3. 文字数の数え方は、画面とサーバーで同じ(組み合わせた絵文字は、組み合わせた数で数える。機能定義書 1.1)
await taro.goto(base + '/');
await taro.waitForSelector('#text-home');
await taro.fill('#text-home', '😀'.repeat(1000));
check('絵文字 1000 文字は「残り 0 文字」', (await taro.textContent('form.composer .counter')).includes('残り 0 文字'));
await taro.fill('#text-home', '😀'.repeat(1001));
check('絵文字 1001 文字は押せない', await taro.locator('form.composer button[type="submit"]').isDisabled());
const family = '👨‍👩‍👧'; // 5 文字(3 つの絵文字 + つなぎの 2 文字)
await taro.fill('#text-home', family.repeat(200));
const frontOk = await taro.locator('form.composer button[type="submit"]').isEnabled();
const backOk = (await api(taro, 'POST', '/api/posts', { form: { text: family.repeat(200) } })).status === 201;
await taro.fill('#text-home', family.repeat(201));
const frontNg = await taro.locator('form.composer button[type="submit"]').isDisabled();
const backNg = (await api(taro, 'POST', '/api/posts', { form: { text: family.repeat(201) } })).status === 400;
check('組み合わせた絵文字も、画面とサーバーで数え方が同じ', frontOk && backOk && frontNg && backNg, `${frontOk}/${backOk}/${frontNg}/${backNg}`);
await taro.fill('#text-home', '');

// 4. 投稿ボタンを素早く 2 回押しても、1 件だけ投稿される
await taro.fill('#text-home', `二度押し ${stamp}`);
await taro.locator('form.composer button[type="submit"]').dblclick();
await taro.waitForTimeout(1500);
const dup = (await api(taro, 'GET', `/api/search/posts?q=${encodeURIComponent(`二度押し ${stamp}`)}`)).body.items.length;
check('二度押ししても 1 件だけ', dup === 1, `${dup}件`);

// 5. 通信に失敗したとき: エラーと「もう一度」が出て、押すと読み込める。投稿に失敗しても入力は消えない
await taro.route('**/api/timeline*', (r) => r.abort());
await taro.reload();
await taro.waitForSelector('button:has-text("もう一度")', { timeout: 8000 }).catch(() => {});
check('一覧の読み込みに失敗するとエラーと「もう一度」', await taro.locator('button:has-text("もう一度")').count() > 0);
await taro.unroute('**/api/timeline*');
await taro.locator('button:has-text("もう一度")').first().click().catch(() => {});
await taro.waitForSelector('article.card', { timeout: 8000 }).catch(() => {});
check('「もう一度」で読み込める', await taro.locator('article.card').count() > 0);
await taro.route('**/api/posts', (r) => (r.request().method() === 'POST' ? r.abort() : r.continue()));
await taro.fill('#text-home', `失敗する投稿 ${stamp}`);
await taro.click('form.composer button[type="submit"]');
await taro.waitForTimeout(1000);
check('投稿に失敗しても入力は残る', (await taro.inputValue('#text-home')) === `失敗する投稿 ${stamp}`);
check('投稿に失敗したらエラーを出す', (await taro.textContent('body')).includes('接続できませんでした'));
await taro.unroute('**/api/posts');

// 6. キーボードだけで: 投稿 → いいね → メニューを開いて Esc
await taro.goto(base + '/');
await taro.waitForSelector('#text-home');
await taro.focus('#text-home');
await taro.keyboard.type(`キーボードだけの投稿 ${stamp}`);
let reached = false;
for (let i = 0; i < 6 && !reached; i++) {
  await taro.keyboard.press('Tab');
  reached = await taro.evaluate(() => document.activeElement?.matches('form.composer button[type="submit"]'));
}
check('Tab で投稿ボタンへ行ける', reached);
await taro.keyboard.press('Enter');
const card = taro.locator(`article.card:has-text("キーボードだけの投稿 ${stamp}")`);
await card.waitFor({ timeout: 5000 }).catch(() => {});
check('Enter で投稿できる', await card.count() === 1);
await card.locator('button.like').focus();
await taro.keyboard.press('Enter');
await taro.waitForTimeout(500);
check('Enter でいいね', (await card.locator('button.like').getAttribute('aria-pressed')) === 'true');
await card.locator('button[aria-label="その他の操作"]').focus();
await taro.keyboard.press('Enter');
check('Enter でメニューが開き、最初の項目が選ばれる', await taro.evaluate(() => document.activeElement?.closest('.menu') !== null));
await taro.keyboard.press('Escape');
check('Esc でメニューが閉じる', (await card.locator('.menu').count()) === 0);

// 7. 画像は 10 枚まで(11 枚目は断って、理由を出す)
await taro.setInputFiles('form.composer input[type="file"]', [...Array(11)].map((_, i) => fixture(`m10-${i}.png`, 20 + i, 20)));
await taro.waitForTimeout(800);
const thumbs = await taro.locator('form.composer .thumb').count();
check('画像を 11 枚選ぶと 10 枚までで、理由を出す', thumbs === 10 && (await taro.textContent('form.composer')).includes('画像は10枚までです'), `${thumbs}枚`);

// 8. ログインに 5 回続けて失敗すると、15 分ロックされる(ken。後片付けでロックを外す)
const anon = await (await browser.newContext()).newPage();
await anon.goto(base + '/login');
for (let i = 0; i < 6; i++) {
  await anon.fill('#login-email', 'ken@example.com');
  await anon.fill('#login-password', 'wrong-password');
  await anon.click('form button[type="submit"]');
  await anon.waitForTimeout(400);
}
check('5 回失敗するとロックの文', (await anon.textContent('body')).includes('15分間ログインできません'));

// 9. 別のタブでログアウトしたあと、残ったタブで画面を移るとログイン画面へ
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const tab1 = await loginAs('mika@example.com', undefined, ctx);
const tab2 = await ctx.newPage();
await tab2.goto(base + '/');
await tab2.waitForSelector('h1:has-text("ホーム")');
await tab1.goto(base + '/settings');
await tab1.click('button:has-text("ログアウト")');
await tab1.waitForSelector('h1:has-text("ログイン")');
await tab2.goto(base + '/notifications');
await tab2.waitForSelector('h1:has-text("ログイン")', { timeout: 5000 }).catch(() => {});
check('ログアウト後に別のタブで画面を移るとログイン画面', tab2.url().includes('/login'));

// 10. URL を直接開く・再読み込みしても、その画面が出る(ビルドした画面をサーバーが配信するときに大事)
for (const path of ['/settings', '/trends', `/posts/${long.body.id}`, '/users/hanako/followers']) {
  await taro.goto(base + path);
  await taro.waitForSelector('h1');
  check(`URL を直接開ける ${path}`, !(await taro.textContent('h1')).includes('見つかりません'));
}

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
