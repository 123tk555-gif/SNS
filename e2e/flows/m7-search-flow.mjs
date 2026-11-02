import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = String(Date.now()).slice(-6);
const browser = await chromium.launch({ channel: 'msedge' });

async function loginAs(email, viewport = { width: 1280, height: 900 }) {
  const page = await (await browser.newContext({ viewport })).newPage();
  page.on('pageerror', (e) => errors.push(`${email}: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(`${email}: ${m.text()}`); });
  await page.goto(base + '/login');
  await page.fill('#login-email', email);
  await page.fill('#login-password', 'password123');
  await page.click('form button[type="submit"]');
  await page.waitForSelector('h1:has-text("ホーム")');
  return page;
}
async function apiPost(page, text) {
  return page.evaluate(async (text) => {
    const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
    const form = new FormData();
    form.append('text', text);
    return (await (await fetch('/api/posts', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: form })).json()).id;
  }, text);
}
async function apiCall(page, method, url) {
  return page.evaluate(async ({ method, url }) => {
    const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
    return (await fetch(url, { method, headers: { 'X-XSRF-TOKEN': token } })).status;
  }, { method, url });
}

const tag = `カフェ巡り${stamp}`;
const taro = await loginAs('taro@example.com');
const hanako = await loginAs('hanako@example.com');
const taroPost = await apiPost(taro, `渋谷のカフェで勉強しました #${tag}`);
await apiPost(hanako, `新宿のカフェでケーキ #${tag} #写真${stamp}`);
await apiCall(hanako, 'PUT', `/api/posts/${taroPost}/like`);

// 投稿の #タグ を押すと、そのタグの投稿一覧
await taro.reload();
const card = taro.locator(`article.card:has-text("渋谷のカフェで勉強しました #${tag}")`);
await card.waitFor();
await card.locator(`.text a:has-text("#${tag}")`).click();
await taro.waitForSelector(`h1:has-text("#${tag}")`);
await taro.waitForSelector('article.card');
check('#タグを押すとタグの投稿一覧', (await taro.locator('article.card').count()) === 2);

// トレンド
await taro.click('nav a.nav-item:has-text("トレンド")');
await taro.waitForSelector('h1:has-text("トレンド")');
await taro.waitForSelector('.main .trend-list li');
const first = await taro.locator('.main .trend-list li').first().innerText();
check('トレンドの1位が今回のタグ(2件)', first.includes(`#${tag}`) && first.includes('2件の投稿'), first.replace(/\n/g, ' '));
check('人気の投稿にいいねされた投稿', (await taro.locator('main article.card').first().innerText()).includes('渋谷のカフェで勉強しました'));
check('右の列にもトレンド', (await taro.locator('aside.right .trend-list').innerText()).includes(`#${tag}`));
await taro.screenshot({ path: `${out}/m7-1-trends.png` });

// 右の列の検索欄から検索
await taro.fill('#side-search', `カフェ　${stamp}`); // 全角の空白で区切る
await taro.press('#side-search', 'Enter');
await taro.waitForSelector('h1:has-text("検索")');
await taro.waitForSelector('main article.card');
check('右の列から検索(すべての言葉を含む投稿)', (await taro.locator('main article.card').count()) === 2);
await taro.screenshot({ path: `${out}/m7-2-search.png` });

// @ で始めるとユーザーのタブ
await taro.fill('#search-q', '@HANA');
await taro.press('#search-q', 'Enter');
await taro.waitForSelector('.user-row');
check('@ で始めるとユーザーのタブ', (await taro.getAttribute('a.tab:has-text("ユーザー")', 'aria-current')) === 'page');
check('ユーザーの検索結果', (await taro.locator('.user-row').first().innerText()).includes('@hanako'));

// # で始めるとハッシュタグのタブ(全角の英数字でも見つかる)
await taro.fill('#search-q', `#写真${stamp.replace(/\d/g, (d) => String.fromCharCode(d.charCodeAt(0) + 0xfee0))}`);
await taro.press('#search-q', 'Enter');
await taro.waitForSelector('main .trend-list li, main .empty');
check('# で始めるとハッシュタグのタブ(全角の数字でも見つかる)', (await taro.locator('main .trend-list').innerText()).includes(`#写真${stamp}`));

// ブラウザの「戻る」で前の検索結果
await taro.goBack();
await taro.waitForSelector('.user-row');
check('「戻る」で前の検索結果', (await taro.inputValue('#search-q')) === '@HANA');

// 見つからないとき・長すぎるとき
await taro.fill('#search-q', `存在しない言葉${stamp}`);
await taro.press('#search-q', 'Enter');
await taro.waitForSelector('main .empty');
check('見つからないときの表示', (await taro.textContent('main .empty')).includes('に一致する結果はありません'));
await taro.fill('#search-q', 'あ'.repeat(101));
await taro.press('#search-q', 'Enter');
check('101文字は「100文字以内」', (await taro.textContent('#search-error')).includes('100文字以内'));

// スマートフォン: 検索の前にトレンドとリンク
const phone = await loginAs('taro@example.com', { width: 375, height: 812 });
await phone.click('nav a.nav-item:has-text("検索")');
await phone.waitForSelector('main .trend-list');
check('スマホの検索画面にトレンドとリンク', await phone.locator('main a:has-text("トレンドをすべて見る")').isVisible());
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m7-3-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
