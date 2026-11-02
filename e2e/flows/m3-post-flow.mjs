import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out, png } from "../lib.mjs";
import { writeFileSync } from "node:fs";
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);

const bigPng = `${out}/big.png`;
writeFileSync(bigPng, png(3000, 2000));
writeFileSync(`${out}/fake.png`, 'not an image');

const browser = await chromium.launch({ channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Failed to load resource')) errors.push(m.text()); });

await page.goto(base + '/login');
await page.fill('#login-email', 'taro@example.com');
await page.fill('#login-password', 'password123');
await page.click('form button[type="submit"]');
await page.waitForSelector('h1:has-text("ホーム")');
check('ログインしてホーム(タイムライン)', true);

// 無限スクロールを試すため、API で投稿を 25 件作る(画面と同じ CSRF の値を使う)
const stamp = Date.now();
await page.evaluate(async (stamp) => {
  const token = document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1];
  for (let i = 1; i <= 25; i++) {
    const form = new FormData();
    form.append('text', `E2E ${stamp} の ${i} 件目`);
    await fetch('/api/posts', { method: 'POST', headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) }, body: form });
  }
}, stamp);
await page.reload();
await page.waitForSelector('article.card');
check('最初は20件', (await page.locator('article.card').count()) === 20, `${await page.locator('article.card').count()} 件`);
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForFunction(() => document.querySelectorAll('article.card').length > 20, null, { timeout: 10000 });
check('下までスクロールすると続きを読み込む', true, `${await page.locator('article.card').count()} 件`);
await page.evaluate(() => window.scrollTo(0, 0));

// 文字数の表示と投稿ボタン
const composer = page.locator('#text-home');
check('空のときは投稿ボタンが押せない', await page.locator('form.composer button[type="submit"]').isDisabled());
await composer.fill('あ'.repeat(1001));
check('1000文字を超えると「オーバー」', (await page.textContent('#counter-home')).includes('1 文字オーバー'));
check('超えたら押せない', await page.locator('form.composer button[type="submit"]').isDisabled());

// 本文 + 画像(縮小される)+ 説明
await composer.fill(`E2E ${stamp} 画像付き #テスト @hanako https://example.com/`);
await page.setInputFiles('form.composer input[type="file"]', [bigPng, `${out}/fake.png`]);
await page.waitForSelector('.thumb');
// 名前が .png だとブラウザは PNG と判断するので、読み込みの段階で断られる
check('画像でないファイルは断る', (await page.textContent('form.composer')).includes('画像を読み込めませんでした')
  && (await page.locator('.thumb').count()) === 1);
await page.fill('#alt-home-0', 'オレンジ色の四角');
await page.screenshot({ path: `${out}/m3-1-composer.png` });
await page.click('form.composer button[type="submit"]');
await page.waitForSelector(`article.card:has-text("${stamp} 画像付き")`);
const card = page.locator('article.card').first();
check('投稿すると先頭に出る', (await card.textContent()).includes('画像付き'));
check('お知らせ「投稿しました」', (await page.textContent('.toast')).includes('投稿しました'));
check('#タグ・@ユーザー・URL がリンクになる', (await card.locator('.text a').count()) === 3);
const img = card.locator('.imgs img');
const natural = await img.evaluate((el) => new Promise((r) => (el.complete ? r([el.naturalWidth, el.naturalHeight]) : (el.onload = () => r([el.naturalWidth, el.naturalHeight])))));
check('画像は長辺2048pxに縮小される', natural[0] === 2048 && natural[1] === 1365, natural.join('x'));
check('入力欄が空に戻る', (await composer.inputValue()) === '');

// 画像の拡大表示
await card.locator('button.img').click();
await page.waitForSelector('dialog[open]');
check('画像の拡大表示と説明', (await page.textContent('dialog[open]')).includes('オレンジ色の四角'));
await page.screenshot({ path: `${out}/m3-2-viewer.png` });
await page.keyboard.press('Escape');
await page.waitForSelector('dialog[open]', { state: 'detached' });

// 編集
await card.locator('button[aria-label="その他の操作"]').click();
await page.click('.menu button:has-text("編集")');
await page.fill('#edit-text', `E2E ${stamp} 編集しました`);
await page.click('dialog button:has-text("保存する")');
// 編集のダイアログはカードの中にあるので、ダイアログが閉じる(保存が終わる)のを待ってから確かめる
await page.waitForSelector('dialog[open]', { state: 'detached' });
const edited = page.locator('article.card').first();
check('編集すると本文が変わり「編集済み」', (await edited.locator('.text').innerText()).includes('編集しました')
  && (await edited.innerText()).includes('(編集済み)'));

// 削除(最初は「やめる」が選ばれている)
await page.locator('article.card').first().locator('button[aria-label="その他の操作"]').click();
await page.click('.menu button:has-text("削除")');
await page.waitForSelector('dialog[open]');
check('削除の確認は「やめる」が選ばれている', await page.evaluate(() => document.activeElement?.textContent === 'やめる'));
await page.click('dialog button:has-text("削除する")');
await page.waitForSelector(`article.card:has-text("${stamp} 編集しました")`, { state: 'detached' });
check('削除すると一覧から消える', true);
await page.reload();
await page.waitForSelector('article.card');
check('再読み込みしても消えたまま', !(await page.content()).includes(`${stamp} 編集しました`));
await page.screenshot({ path: `${out}/m3-3-home.png` });

// スマートフォン: 右下の「投稿する」からダイアログで投稿
const phone = await context.newPage(); await phone.setViewportSize({ width: 375, height: 812 });
await phone.goto(base + '/');
await phone.waitForSelector('article.card');
check('スマホでは投稿欄を出さない', !(await phone.locator('#text-home').isVisible()));
await phone.click('button.fab');
await phone.fill('#text-dialog', `E2E ${stamp} スマホから`);
await phone.click('dialog button[type="submit"]');
await phone.waitForSelector(`article.card:has-text("${stamp} スマホから")`);
check('スマホのダイアログから投稿', !(await phone.locator('dialog[open]').count()));
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m3-4-phone.png` });

// 他人の投稿にはメニューが出ない(hanako としてログインして確かめる)
const other = await browser.newContext();
const hanako = await other.newPage();
await hanako.goto(base + '/login');
await hanako.fill('#login-email', 'hanako@example.com');
await hanako.fill('#login-password', 'password123');
await hanako.click('form button[type="submit"]');
await hanako.waitForSelector('article.card');
const taroCard = hanako.locator(`article.card:has-text("${stamp} スマホから")`);
await taroCard.locator('button[aria-label="その他の操作"]').click();
check('他人の投稿のメニューに編集・削除がない', !/編集|削除/.test(await taroCard.locator('.menu').innerText()));

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
