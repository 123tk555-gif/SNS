import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { openTab, out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = Date.now();
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'msedge' });

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

// 1. 一番上を見ているとき: 新着はすぐには出ず、「新しい投稿が 1 件あります」ボタンが出る。押すと先頭に出る
const newbar = '.newbar button';
await apiPost(taro, `M6 ${stamp} リアルタイム1`);
await hanako.waitForSelector(newbar, { timeout: 5000 });
check('新着が届くと「新しい投稿が 1 件あります」', (await hanako.textContent(newbar)).includes('新しい投稿が 1 件あります'));
check('押すまでは一覧に足さない', !(await hanako.content()).includes(`${stamp} リアルタイム1`));
await apiPost(taro, `M6 ${stamp} リアルタイム1b`);
await hanako.waitForFunction((sel) => document.querySelector(sel)?.textContent.includes('2 件'), newbar, { timeout: 5000 });
check('届くたびに件数が増える', true);
await hanako.screenshot({ path: `${out}/m6-1-newbar-top.png` });
await hanako.click(newbar);
await hanako.waitForSelector(`article.card:has-text("${stamp} リアルタイム1b")`);
const first = hanako.locator('article.card').first();
check('押すと新着が先頭に出る(新しい順)', (await first.innerText()).includes('リアルタイム1b')
  && (await hanako.locator('article.card').nth(1).innerText()).includes('リアルタイム1'));
check('新着の印', (await first.innerText()).includes('新着'));
check('押すとボタンが消える', (await hanako.locator('.newbar').count()) === 0);
check('押したあと、先頭の投稿に移る(キーボードの人のため)',
  await hanako.evaluate(() => document.activeElement?.closest('article.card') === document.querySelector('main article.card')));

// 自分の投稿は、ボタンを待たずにすぐ出る
await hanako.fill('#text-home', `M6 ${stamp} はなこの自分の投稿`);
await hanako.click('form.composer button[type="submit"]');
await hanako.waitForSelector(`article.card:has-text("${stamp} はなこの自分の投稿")`, { timeout: 5000 });
check('自分の投稿は、すぐ先頭に出る', (await hanako.locator('.newbar').count()) === 0);

// 2. 下を読んでいるとき: 見ている投稿が動かず、ボタンが画面の上部に出る。押すと一番上へ
await hanako.evaluate(() => window.scrollTo(0, 1200));
await hanako.waitForTimeout(300);
const watched = await hanako.locator('article.card').nth(8).elementHandle(); // 同じカードを測り続ける
const beforeTop = await watched.evaluate((el) => el.getBoundingClientRect().top);
await apiPost(taro, `M6 ${stamp} リアルタイム2`);
await hanako.waitForSelector(newbar, { timeout: 5000 });
await hanako.waitForTimeout(300);
const afterTop = await watched.evaluate((el) => el.getBoundingClientRect().top);
check('下を読んでいる途中でも、見ている投稿は動かない', Math.abs(afterTop - beforeTop) < 2, `${beforeTop} → ${afterTop}`);
const barTop = await hanako.locator(newbar).evaluate((el) => el.getBoundingClientRect().top);
check('ボタンは、スクロールしていても画面の上部に見える', barTop >= 0 && barTop < 200, `top=${barTop}`);
await hanako.screenshot({ path: `${out}/m6-1-newbar.png` });
await hanako.click(newbar);
await hanako.waitForFunction(() => window.scrollY < 50);
check('押すと一番上に戻り、新着が先頭に出る', (await hanako.locator('article.card').first().innerText()).includes('リアルタイム2'));

// 3. 削除すると、開いている画面からも、ためている新着からも消える
const deleteId = await apiPost(taro, `M6 ${stamp} 消える投稿`);
await hanako.waitForSelector(newbar, { timeout: 5000 });
await apiCall(taro, 'DELETE', `/api/posts/${deleteId}`);
await hanako.waitForSelector('.newbar', { state: 'detached', timeout: 5000 }).then(
  () => check('ためている間に削除された投稿は、件数から外れる(0 件ならボタンも消える)', true),
  () => check('ためている間に削除された投稿は、件数から外れる(0 件ならボタンも消える)', false));
const deleteId2 = await apiPost(taro, `M6 ${stamp} 表示後に消える投稿`);
await hanako.waitForSelector(newbar, { timeout: 5000 });
await hanako.click(newbar);
await hanako.waitForSelector(`article.card:has-text("${stamp} 表示後に消える投稿")`);
await apiCall(taro, 'DELETE', `/api/posts/${deleteId2}`);
await hanako.waitForSelector(`article.card:has-text("${stamp} 表示後に消える投稿")`, { state: 'detached', timeout: 5000 });
check('削除された投稿が画面から消える', true);

// 4. 「フォロー中」: フォローしていない人の新着は出ず、フォローしたら出る
await openTab(hanako, 'フォロー中');
await hanako.waitForTimeout(500);
await apiPost(taro, `M6 ${stamp} フォロー前`);
await hanako.waitForTimeout(1500);
check('フォロー中に、フォローしていない人の新着は出ない(ボタンも出ない)', !(await hanako.content()).includes(`${stamp} フォロー前`)
  && (await hanako.locator('.newbar').count()) === 0);
await apiCall(hanako, 'PUT', '/api/users/taro_dev/follow');
await apiPost(taro, `M6 ${stamp} フォロー後`);
await hanako.waitForSelector(newbar, { timeout: 5000 });
await hanako.click(newbar);
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
// つなぎ直すと、切れている間の投稿もボタンに入る
await hanako.waitForSelector(newbar, { timeout: 15000 }).then(() => hanako.click(newbar), () => {});
await hanako.waitForSelector(`article.card:has-text("${stamp} 切れている間の投稿")`, { timeout: 5000 }).then(
  () => check('つなぎ直すと、切れている間の投稿を取り込む', true),
  () => check('つなぎ直すと、切れている間の投稿を取り込む', false));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
