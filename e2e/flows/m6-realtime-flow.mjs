import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = Date.now();
const browser = await chromium.launch({ channel: 'msedge' });

async function loginAs(email) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('pageerror', (e) => errors.push(`${email}: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(`${email}: ${m.text()}`); });
  await page.goto(base + '/login');
  await page.fill('#login-email', email);
  await page.fill('#login-password', 'password123');
  await page.click('form button[type="submit"]');
  await page.waitForSelector('h1:has-text("ホーム")');
  return page;
}
// API で投稿する(画面と同じ CSRF の値を使う)
async function apiPost(page, text) {
  return page.evaluate(async (text) => {
    const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
    const form = new FormData();
    form.append('text', text);
    const res = await fetch('/api/posts', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: form });
    return (await res.json()).id;
  }, text);
}
async function apiCall(page, method, url) {
  return page.evaluate(async ({ method, url }) => {
    const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
    return (await fetch(url, { method, headers: { 'X-XSRF-TOKEN': token } })).status;
  }, { method, url });
}

const taro = await loginAs('taro@example.com');
const hanako = await loginAs('hanako@example.com');
// 読み込みの途中でスクロールできるよう、先に投稿を増やしておく
for (let i = 1; i <= 15; i++) await apiPost(taro, `M6 ${stamp} 準備の投稿 ${i}`);
await hanako.reload();
await hanako.waitForSelector('article.card');
await hanako.waitForTimeout(1000); // SSE につながるのを待つ

// 1. 一番上を見ているとき: 新着がそのまま先頭に出る
await apiPost(taro, `M6 ${stamp} リアルタイム1`);
await hanako.waitForSelector(`article.card:has-text("${stamp} リアルタイム1")`, { timeout: 5000 });
const first = hanako.locator('article.card').first();
check('再読み込みせずに新着が先頭に出る', (await first.innerText()).includes('リアルタイム1'));
check('新着の印', (await first.innerText()).includes('新着'));

// 2. 下を読んでいるとき: 見ている投稿が動かず、「新しい投稿 ○件」が出る
await hanako.evaluate(() => window.scrollTo(0, 1200));
await hanako.waitForTimeout(300);
const watched = await hanako.locator('article.card').nth(8).elementHandle(); // 同じカードを測り続ける
const beforeTop = await watched.evaluate((el) => el.getBoundingClientRect().top);
await apiPost(taro, `M6 ${stamp} リアルタイム2`);
await hanako.waitForSelector('.newbar button', { timeout: 5000 });
await hanako.waitForTimeout(300);
const afterTop = await watched.evaluate((el) => el.getBoundingClientRect().top);
check('下を読んでいる途中でも、見ている投稿は動かない', Math.abs(afterTop - beforeTop) < 2, `${beforeTop} → ${afterTop}`);
check('「↑ 新しい投稿 1件」が出る', (await hanako.textContent('.newbar button')).includes('新しい投稿 1件'));
await hanako.screenshot({ path: `${out}/m6-1-newbar.png` });
await hanako.click('.newbar button');
await hanako.waitForFunction(() => window.scrollY < 50);
check('押すと一番上に戻り、ボタンが消える', (await hanako.locator('.newbar').count()) === 0);

// 3. 削除すると、開いている画面からも消える
const deleteId = await apiPost(taro, `M6 ${stamp} 消える投稿`);
await hanako.waitForSelector(`article.card:has-text("${stamp} 消える投稿")`);
await apiCall(taro, 'DELETE', `/api/posts/${deleteId}`);
await hanako.waitForSelector(`article.card:has-text("${stamp} 消える投稿")`, { state: 'detached', timeout: 5000 });
check('削除された投稿が画面から消える', true);

// 4. 「フォロー中」: フォローしていない人の新着は出ず、フォローしたら出る
await hanako.click('a.tab:has-text("フォロー中")');
await hanako.waitForSelector('main .empty, main article.card');
await hanako.waitForTimeout(500);
await apiPost(taro, `M6 ${stamp} フォロー前`);
await hanako.waitForTimeout(1500);
check('フォロー中に、フォローしていない人の新着は出ない', !(await hanako.content()).includes(`${stamp} フォロー前`));
await apiCall(hanako, 'PUT', '/api/users/taro_dev/follow');
await apiPost(taro, `M6 ${stamp} フォロー後`);
await hanako.waitForSelector(`article.card:has-text("${stamp} フォロー後")`, { timeout: 5000 });
check('フォロー中に、フォローしている人の新着は出る', true);

// 5. 通知: いいねされると、再読み込みせずにバッジが増える
const hanakoPost = await apiPost(hanako, `M6 ${stamp} はなこの投稿`);
await apiCall(taro, 'PUT', `/api/posts/${hanakoPost}/like`);
await hanako.waitForSelector('nav a.nav-item:has-text("通知") .badge', { timeout: 5000 });
check('いいねされるとバッジが出る', (await hanako.textContent('nav a.nav-item:has-text("通知") .badge')) === '1');
check('読み上げソフトにも伝える', (await hanako.locator('[aria-live="polite"].sr-only').first().textContent()).includes('通知が届きました'));
await hanako.screenshot({ path: `${out}/m6-2-badge.png` });

// 6. 通知の画面: 未読の印、開くとバッジが消える
await hanako.click('nav a.nav-item:has-text("通知")');
await hanako.waitForSelector('.notif');
const notif = hanako.locator('.notif').first();
check('通知の文', (await notif.innerText()).includes('たろうさんがあなたの投稿にいいねしました'));
check('未読の印', (await notif.innerText()).includes('未読'));
await hanako.waitForFunction(() => !document.querySelector('nav a.nav-item .badge'));
check('開くとバッジが消える', true);
await hanako.screenshot({ path: `${out}/m6-3-notifications.png` });

// 開いている間に届いた通知は、先頭に足して既読にする(バッジは出ない)
await apiCall(taro, 'DELETE', '/api/users/hanako/follow');
await apiCall(taro, 'PUT', '/api/users/hanako/follow');
await hanako.waitForSelector('.notif:has-text("あなたをフォローしました")', { timeout: 5000 });
await hanako.waitForTimeout(800);
check('開いている間に届いた通知は先頭に出る', (await hanako.locator('.notif').first().innerText()).includes('フォローしました'));
check('開いている間はバッジが出ない', (await hanako.locator('nav a.nav-item .badge').count()) === 0);

// 通知を押すと対象の投稿へ
await hanako.locator(`.notif:has-text("${stamp} はなこの投稿") a.body`).click();
await hanako.waitForSelector(`article.card.big:has-text("${stamp} はなこの投稿")`);
check('通知を押すと対象の投稿へ', true);
await hanako.goBack();
await hanako.waitForSelector('.notif');
await hanako.reload();
await hanako.waitForSelector('.notif');
check('開き直すと未読の印は消える', !(await hanako.locator('.notif').first().innerText()).includes('未読'));

// 7. 通信が切れて、つなぎ直したときに、その間の投稿を取り込む
await hanako.click('nav a.nav-item:has-text("ホーム")');
await hanako.waitForSelector('article.card');
await hanako.context().setOffline(true);
await hanako.waitForTimeout(1000);
await apiPost(taro, `M6 ${stamp} 切れている間の投稿`);
await hanako.context().setOffline(false);
await hanako.waitForSelector(`article.card:has-text("${stamp} 切れている間の投稿")`, { timeout: 15000 }).then(
  () => check('つなぎ直すと、切れている間の投稿を取り込む', true),
  () => check('つなぎ直すと、切れている間の投稿を取り込む', false));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
