import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { fixture, out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = String(Date.now()).slice(-6);
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
async function apiPost(page, text) {
  return page.evaluate(async (text) => {
    const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
    const form = new FormData();
    form.append('text', text);
    return (await (await fetch('/api/posts', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: form })).json()).id;
  }, text);
}
const toast = async (page, text) => {
  try { await page.waitForSelector(`text=${text}`, { timeout: 4000 }); return true; } catch { return false; }
};

const taro = await loginAs('taro@example.com');
const hanako = await loginAs('hanako@example.com');
const ken = await loginAs('ken@example.com');
await apiPost(hanako, `はなこのE2E投稿${stamp}`);
await apiPost(ken, `けんのE2E投稿${stamp}`);

// ---- 投稿カードの「…」からミュート ----
await taro.reload();
const hCard = taro.locator(`article.card:has-text("はなこのE2E投稿${stamp}")`);
await hCard.waitFor();
await hCard.locator('button[aria-label="その他の操作"]').click();
check('他人の投稿のメニューにミュート・ブロック',
  (await hCard.locator('.menu').innerText()).includes('@hanako さんをミュート') && (await hCard.locator('.menu').innerText()).includes('@hanako さんをブロック'));
await hCard.locator('.menu button:has-text("ミュート")').click();
check('ミュートのお知らせ', await toast(taro, '@hanako さんをミュートしました'));
await taro.waitForTimeout(300);
check('ミュートすると一覧から消える', (await taro.locator(`article.card:has-text("はなこのE2E投稿${stamp}")`).count()) === 0);
await taro.reload();
await taro.waitForSelector('article.card');
check('読み込み直しても出ない', (await taro.locator(`article.card:has-text("はなこのE2E投稿${stamp}")`).count()) === 0);

// プロフィール: ミュート中のチップ・投稿は見える
await taro.goto(base + '/users/hanako');
await taro.waitForSelector('section.profile');
check('プロフィールに「ミュート中」', await taro.locator('.profile .chip:has-text("ミュート中")').isVisible());
await taro.waitForSelector('main article.card');
check('プロフィールではミュート中の人の投稿も見える', (await taro.locator(`article.card:has-text("はなこのE2E投稿${stamp}")`).count()) === 1);
await taro.click('.profile-top button[aria-label="その他の操作"]');
await taro.click('.menu button:has-text("ミュートを解除")');
check('ミュート解除のお知らせ', await toast(taro, '@hanako さんのミュートを解除しました'));
check('チップが消える', (await taro.locator('.profile .chip:has-text("ミュート中")').count()) === 0);

// ---- プロフィールからブロック(D-04) ----
await taro.click('.profile-top button[aria-label="その他の操作"]');
await taro.click('.menu button:has-text("ブロックする")');
await taro.waitForSelector('dialog[open]');
check('D-04 の題名', (await taro.textContent('dialog[open] h2')).includes('@hanako さんをブロックしますか?'));
check('D-04 は最初「やめる」を選んでいる', await taro.evaluate(() => document.activeElement?.id === 'block-cancel'));
await taro.screenshot({ path: `${out}/m8-1-block-dialog.png` });
await taro.click('dialog[open] button.btn-danger');
await taro.waitForSelector('.blocked-box');
check('ブロックすると「ブロックしています」', (await taro.textContent('.blocked-box')).includes('@hanako さんをブロックしています'));
check('フォローボタンが消える', (await taro.locator('.profile-top .buttons button:has-text("フォロー")').count()) === 0);

// はなこ側: たろうのプロフィールは見つからない
await hanako.goto(base + '/users/taro_dev');
await hanako.waitForSelector('text=このユーザーは見つかりませんでした');
check('ブロックされた側からはプロフィールが見つからない', true);

// ---- 投稿カードからブロック ----
await taro.goto(base + '/');
const kCard = taro.locator(`article.card:has-text("けんのE2E投稿${stamp}")`);
await kCard.waitFor();
await kCard.locator('button[aria-label="その他の操作"]').click();
await kCard.locator('.menu button:has-text("ブロック")').click();
await taro.click('dialog[open] button.btn-danger');
await taro.waitForTimeout(400);
check('投稿カードからブロックすると一覧から消える', (await taro.locator(`article.card:has-text("けんのE2E投稿${stamp}")`).count()) === 0);

// ---- 設定画面 ----
await taro.goto(base + '/settings');
await taro.waitForSelector('h1:has-text("設定")');
await taro.waitForSelector('#profile-name');
const blockRows = taro.locator('section[aria-labelledby="settings-blocks"] .user-row');
await blockRows.first().waitFor();
check('ブロック中の一覧に2人', (await blockRows.count()) === 2);
check('ミュート中は0件の表示', (await taro.textContent('section[aria-labelledby="settings-mutes"]')).includes('ミュートしているユーザーはいません'));
const hRow = blockRows.filter({ hasText: '@hanako' });
await hRow.locator('button').click();
await hRow.locator('text=解除しました').waitFor();
check('解除しても一覧に残り「もう一度ブロックする」', (await hRow.locator('button').innerText()).includes('もう一度ブロックする'));
await hRow.locator('button').click();
await taro.waitForTimeout(300);
check('もう一度ブロックできる', (await hRow.locator('button').innerText()).includes('ブロックを解除'));
// 後片付け: 両方解除
for (const row of await blockRows.all()) await row.locator('button').click();
await taro.waitForTimeout(500);

// 表示名・自己紹介
const oldName = await taro.inputValue('#profile-name');
await taro.fill('#profile-name', '');
await taro.click('form button[type="submit"]:has-text("保存する")');
check('表示名が空ならエラー', (await taro.textContent('#profile-name-error')).includes('表示名を入力してください'));
await taro.fill('#profile-bio', 'あ'.repeat(161));
check('自己紹介の文字数オーバー表示', (await taro.textContent('#profile-bio-hint')).includes('1 文字オーバー'));
await taro.fill('#profile-name', `たろうE2E${stamp}`);
await taro.fill('#profile-bio', `E2E の自己紹介 ${stamp}`);
await taro.click('form button[type="submit"]:has-text("保存する")');
check('プロフィールを保存しました', await toast(taro, 'プロフィールを保存しました'));
check('サーバーにも保存されている', (await taro.evaluate(async () => (await (await fetch('/api/auth/me')).json()).displayName)) === `たろうE2E${stamp}`);

// アイコン画像
await taro.setInputFiles('#avatar-file', fixture('fixture-small.png', 60, 60));
check('アイコン画像を変更しました', await toast(taro, 'アイコン画像を変更しました'));
const avatarStyle = await taro.getAttribute('.image-field .avatar.lg', 'style');
check('アイコンのプレビューが画像になる', avatarStyle.includes('/media/avatars/'));
await taro.setInputFiles('#header-file', fixture('fixture-wide.png', 300, 100));
check('ヘッダー画像を変更しました', await toast(taro, 'ヘッダー画像を変更しました'));
await taro.screenshot({ path: `${out}/m8-2-settings.png`, fullPage: true });
await taro.click('.field:has(#avatar-file) button:has-text("削除")');
check('アイコン画像を削除しました', await toast(taro, 'アイコン画像を削除しました'));
await taro.click('.field:has(#header-file) button:has-text("削除")');
check('ヘッダー画像を削除しました', await toast(taro, 'ヘッダー画像を削除しました'));
// 元に戻す
await taro.fill('#profile-name', oldName);
await taro.fill('#profile-bio', 'Java と React を勉強中です。#SpringBoot でアプリを作っています');
await taro.click('form button[type="submit"]:has-text("保存する")');
await toast(taro, 'プロフィールを保存しました');

// 表示モード
await taro.check('input[name="theme"][value="dark"]');
check('ダークを選ぶとすぐ切り替わる', (await taro.evaluate(() => document.documentElement.dataset.theme)) === 'dark');
await taro.reload();
await taro.waitForSelector('#profile-name');
check('読み込み直してもダークのまま', (await taro.evaluate(() => document.documentElement.dataset.theme)) === 'dark');
check('ダークのラジオが選ばれている', await taro.isChecked('input[name="theme"][value="dark"]'));
await taro.screenshot({ path: `${out}/m8-3-settings-dark.png` });
await taro.check('input[name="theme"][value="system"]');
check('端末に合わせるで data-theme が消える', (await taro.evaluate(() => document.documentElement.dataset.theme)) === undefined);
check('メールアドレスの表示', (await taro.textContent('section[aria-labelledby="settings-account"]')).includes('taro@example.com'));

// スマートフォン幅
const phone = await loginAs('taro@example.com', { width: 375, height: 812 });
await phone.goto(base + '/settings');
await phone.waitForSelector('#profile-name');
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m8-4-phone.png`, fullPage: true });

// ログアウト
await taro.click('section[aria-labelledby="settings-account"] button:has-text("ログアウト")');
await taro.waitForSelector('text=ログアウトしました');
check('設定画面からログアウト', taro.url().endsWith('/login'));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
