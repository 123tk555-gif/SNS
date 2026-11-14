// お試しログイン(F-AUTH-08)。登録せずに入れること、お試し中の表示、投稿できること、プロフィールは変えられないこと
import { chromium } from 'playwright-core';
import { out } from '../lib.mjs';

const base = process.argv[2] ?? 'http://localhost:5173';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = Date.now();
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'msedge' });

async function open(viewport) {
  const page = await (await browser.newContext({ viewport })).newPage();
  page.on('pageerror', (e) => errors.push(e.message));
  return page;
}

// ---- PC: ログイン画面のボタンから入る ----
const page = await open({ width: 1280, height: 900 });
await page.goto(`${base}/login`);
const tryButton = page.locator('button:has-text("登録せずに試す")');
await tryButton.waitFor();
check('ログイン画面に「登録せずに試す」がある', await tryButton.isVisible());
check('書き込みが消えることを、押す前に伝える', (await page.textContent('.guest-try')).includes('毎日 4 時に消えます'));
await page.screenshot({ path: `${out}/m12-1-login.png` });

await tryButton.click();
await page.waitForSelector('h1:has-text("ホーム")');
check('押すと、登録せずにホームに入れる', true);
check('お試し中であることが、画面の上に出る', (await page.textContent('.guest-banner')).includes('お試し中です'));

// 投稿できる(みんなに見える)
await page.fill('#text-home', `M12 ${stamp} お試しの投稿`);
await page.click('form.composer button[type="submit"]');
await page.waitForSelector(`article.card:has-text("${stamp} お試しの投稿")`);
check('お試しのアカウントで投稿できる', (await page.locator('article.card').first().innerText()).includes('お試しユーザー'));
await page.screenshot({ path: `${out}/m12-2-home.png` });

// 設定: プロフィールは変えられない
await page.click('nav a.nav-item:has-text("設定")');
await page.waitForSelector('h1:has-text("設定")');
const settings = await page.textContent('main');
check('設定では、プロフィールを変えられないことを伝える', settings.includes('プロフィールを変えられません'));
check('プロフィールの入力欄は出ない', (await page.locator('main form input[type="text"], main form textarea').count()) === 0);
check('メールアドレスの代わりに、お試しのアカウントであることを出す', settings.includes('お試しのアカウントでログインしています'));

// ログアウトすると、ログイン画面に戻る
await page.click('button:has-text("ログアウト")');
await page.waitForSelector('button:has-text("登録せずに試す")');
check('ログアウトすると、ログイン画面に戻る', true);

// ---- スマートフォン: 横にはみ出さない ----
const phone = await open({ width: 375, height: 800 });
await phone.goto(`${base}/login`);
await phone.click('button:has-text("登録せずに試す")');
await phone.waitForSelector('.guest-banner');
check('スマホ幅でも、横スクロールが出ない',
  await phone.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth));
await phone.screenshot({ path: `${out}/m12-3-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
