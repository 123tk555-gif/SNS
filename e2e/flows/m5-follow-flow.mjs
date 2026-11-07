import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { openTab, out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = Date.now();
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'msedge' });

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
async function postAs(page, text) {
  await page.goto(base + '/');
  await page.fill('#text-home', text);
  await page.click('form.composer button[type="submit"]');
  await page.waitForSelector(`article.card:has-text("${text}")`);
}

const hanako = await loginAs('hanako@example.com');
await postAs(hanako, `M5 ${stamp} はなこの投稿`);
const ken = await loginAs('ken@example.com');
await postAs(ken, `M5 ${stamp} けんの投稿`);

const taro = await loginAs('taro@example.com');
// まだ誰もフォローしていない:「フォロー中」には自分の投稿だけ(今はなし)
await openTab(taro, 'フォロー中');
check('フォロー前の「フォロー中」に他人の投稿は出ない', !(await taro.content()).includes(`${stamp} はなこの投稿`));

// 「すべて」から、はなこの名前を押してプロフィールへ
await openTab(taro, 'すべて');
const hanakoCard = taro.locator(`article.card:has-text("${stamp} はなこの投稿")`);
await hanakoCard.waitFor();
await hanakoCard.locator('a.name').click();
await taro.waitForSelector('section.profile');
check('名前を押すとプロフィール', (await taro.textContent('section.profile')).includes('@hanako'));
await taro.waitForSelector(`article.card:has-text("${stamp} はなこの投稿")`, { timeout: 10000 }).catch(() => {});
check('プロフィールに投稿が出る', (await taro.content()).includes(`${stamp} はなこの投稿`));
const followers = async () => Number((await taro.locator('.counts a').nth(1).innerText()).match(/\d+/)[0]);
const before = await followers();

// フォローする
await taro.click('section.profile button:has-text("フォローする")');
await taro.waitForSelector('section.profile button:has-text("フォロー")');
await taro.waitForSelector('.toast:has-text("フォローしました")');
const followButton = taro.locator('section.profile .buttons > button');
check('フォローするとボタンが「フォロー中」系になる', /フォロー中|フォローを解除/.test(await followButton.innerText()));
check('フォロワー数が 1 増える', (await followers()) === before + 1, `${before} → ${await followers()}`);
// キーボードで選んだとき(Tab で一度外してから戻る)と、マウスを乗せたとき
await taro.mouse.move(0, 0);
await taro.keyboard.press('Shift+Tab');
await taro.keyboard.press('Tab');
check('フォロー中のボタンをキーボードで選ぶと「フォローを解除」', (await followButton.innerText()) === 'フォローを解除',
  await followButton.innerText());
await taro.keyboard.press('Shift+Tab');
check('選んでいないときは「フォロー中」', (await followButton.innerText()) === 'フォロー中');
await followButton.hover();
check('マウスを乗せると「フォローを解除」', (await followButton.innerText()) === 'フォローを解除');
await taro.screenshot({ path: `${out}/m5-1-profile.png` });

// 「フォロー中」のタイムラインに、はなこの投稿が出る(けんの投稿は出ない)
await taro.click('nav a.nav-item:has-text("ホーム")');
await openTab(taro, 'フォロー中');
await taro.waitForSelector(`article.card:has-text("${stamp} はなこの投稿")`);
check('フォロー中のタイムラインにフォローした人の投稿', true);
check('フォローしていない人の投稿は出ない', !(await taro.content()).includes(`${stamp} けんの投稿`));

// 自分のプロフィール →「フォロー」→ 一覧にはなこ
await taro.click('nav a.nav-item:has-text("プロフィール")');
await taro.waitForSelector('section.profile');
check('自分のプロフィールに「プロフィールを編集」', (await taro.textContent('section.profile')).includes('プロフィールを編集'));
check('自分のプロフィールにフォローボタンはない', (await taro.locator('section.profile button:has-text("フォロー")').count()) === 0);
await taro.click('.counts a:has-text("フォロー")');
await taro.waitForSelector('h1:has-text("フォロー中")');
const row = taro.locator('.user-row:has-text("@hanako")');
await row.waitFor();
check('フォロー中の一覧に出る', true);
await taro.screenshot({ path: `${out}/m5-2-following.png` });

// はなこから見ると、たろうに「フォローされています」が付く
await hanako.goto(base + '/users/taro_dev');
await hanako.waitForSelector('section.profile');
check('相手のプロフィールに「フォローされています」', (await hanako.textContent('section.profile')).includes('フォローされています'));
await hanako.click('.counts a:has-text("フォロワー")');
await hanako.waitForSelector('h1:has-text("フォロワー")');
check('はなこのフォロワー一覧ではなく、たろうのフォロワー一覧', (await hanako.textContent('.topbar')).includes('@taro_dev'));

// 一覧からフォローを解除する
await row.locator('button').click();
await taro.waitForSelector('.toast:has-text("フォローを解除しました")');
check('一覧からフォロー解除', (await row.locator('button').innerText()) === 'フォローする');

// 存在しないユーザー
await taro.goto(base + '/users/no_such_user_xyz');
await taro.waitForSelector('h1:has-text("ページが見つかりません")');
check('存在しないユーザー', true);

// スマートフォン: 自分のプロフィールにブックマーク・設定へのリンク
const phone = await loginAs('taro@example.com', { width: 375, height: 812 });
await phone.click('nav a.nav-item:has-text("プロフィール")');
await phone.waitForSelector('section.profile');
check('スマホのプロフィールにブックマーク・設定のリンク', await phone.locator('.mobile-links a:has-text("ブックマーク")').isVisible());
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m5-3-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
