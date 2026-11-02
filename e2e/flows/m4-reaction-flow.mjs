import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out } from '../lib.mjs';
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);
const stamp = Date.now();

const browser = await chromium.launch({ channel: 'msedge' });

async function loginAs(email, viewport = { width: 1280, height: 900 }) {
  const context = await browser.newContext({ viewport });
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

// はなこが投稿する
const hanako = await loginAs('hanako@example.com');
await hanako.fill('#text-home', `M4 ${stamp} はなこの投稿`);
await hanako.click('form.composer button[type="submit"]');
await hanako.waitForSelector(`article.card:has-text("${stamp} はなこの投稿")`);

// たろうがいいね・ブックマーク
const taro = await loginAs('taro@example.com');
await taro.reload();
const card = taro.locator(`article.card:has-text("${stamp} はなこの投稿")`);
await card.waitFor();
const like = card.locator('button.act.like');
await like.click();
await taro.waitForFunction((s) => document.querySelector(`article.card button.act.like[aria-pressed="true"]`) !== null, stamp);
check('いいねを押すと「いいね済み 1」', (await like.innerText()).includes('いいね済み 1') && (await like.getAttribute('aria-pressed')) === 'true');
await taro.reload();
await card.waitFor();
check('再読み込みしても「いいね済み」', (await card.locator('button.act.like').innerText()).includes('いいね済み 1'));

// いいねの保存に失敗したら元に戻り、エラーを出す
await taro.route('**/api/posts/*/like', (route) => route.abort());
await card.locator('button.act.like').click();
await taro.waitForSelector('.toast.error');
check('保存に失敗したら元に戻る', (await card.locator('button.act.like').innerText()).includes('いいね済み 1'));
check('失敗したらエラーを出す', (await taro.textContent('.toast')).includes('エラー:'));
await taro.unroute('**/api/posts/*/like');
await taro.click('.toast button:has-text("閉じる")');

await card.locator('button.act.bm').click();
await taro.waitForSelector('.toast:has-text("ブックマークしました")');
check('ブックマーク済みの表示', (await card.locator('button.act.bm').innerText()).includes('ブックマーク済み'));

// はなこの画面でも、いいね数が見える(はなこ自身はいいねしていない)
await hanako.reload();
const hanakoCard = hanako.locator(`article.card:has-text("${stamp} はなこの投稿")`);
await hanakoCard.waitFor();
check('他の人の画面でもいいね数が増える', (await hanakoCard.locator('button.act.like').innerText()).includes('いいね 1'));

// ブックマークの画面
await taro.click('nav a:has-text("ブックマーク")');
await taro.waitForSelector('h1:has-text("ブックマーク")');
const bmCard = taro.locator(`article.card:has-text("${stamp} はなこの投稿")`);
await bmCard.waitFor();
check('ブックマークの一覧に出る', true);
await bmCard.locator('button.act.bm').click();
await taro.waitForSelector('.toast:has-text("ブックマークを解除しました")');
check('解除しても画面を開き直すまで一覧に残る', (await bmCard.count()) === 1 && (await bmCard.locator('button.act.bm').innerText()) === 'ブックマーク');
await taro.reload();
await taro.waitForSelector('h1:has-text("ブックマーク")');
await taro.waitForTimeout(800);
check('開き直すと一覧から消える', (await bmCard.count()) === 0);

// 返信: カードの「返信」から投稿詳細へ。返信欄にキーボードの位置がある
await taro.click('nav a:has-text("ホーム")');
await card.waitFor();
await card.locator('a.act:has-text("返信")').click();
await taro.waitForSelector('h1:has-text("投稿")');
await taro.waitForSelector('#text-reply');
await taro.waitForTimeout(300);
check('「返信」から来ると返信欄にキーボードの位置', await taro.evaluate(() => document.activeElement?.id === 'text-reply'));
check('返信欄に「@hanako さんへの返信」', (await taro.textContent('form.composer')).includes('@hanako さんへの返信'));
check('まだ返信がないときの表示', (await taro.textContent('main')).includes('まだ返信はありません'));
await taro.fill('#text-reply', `M4 ${stamp} たろうの返信`);
await taro.click('form.composer button[type="submit"]');
await taro.waitForSelector(`article.card:has-text("${stamp} たろうの返信")`);
check('返信すると一覧に出る', true);
check('お知らせ「返信しました」', (await taro.textContent('.toast')).includes('返信しました'));
check('投稿本体の返信数が増える', (await taro.locator('article.card.big a.act').innerText()).includes('返信 1'));
await taro.screenshot({ path: `${out}/m4-1-detail.png`, fullPage: true });

// 返信の詳細: 返信先の投稿が上に出る
await taro.locator(`article.card:has-text("${stamp} たろうの返信") a.time`).click();
await taro.waitForSelector(`article.card.big:has-text("${stamp} たろうの返信")`);
await taro.waitForSelector('article.card:not(.big):has-text("はなこの投稿")', { timeout: 5000 }).catch(() => {});
check('返信の詳細では返信先が上に出る', (await taro.locator('article.card').first().innerText()).includes('はなこの投稿'));
check('「@hanako さんへの返信」の表示', (await taro.locator('article.card.big .reply-to').innerText()).includes('@hanako さんへの返信'));

// ホームのタイムラインには返信が出ない
await taro.click('nav a:has-text("ホーム")');
await taro.waitForSelector('h1:has-text("ホーム")'); // 詳細画面にも同じカードがあるので、画面が切り替わるのを待つ
await card.waitFor();
check('返信はタイムラインに出ない', !(await taro.content()).includes(`${stamp} たろうの返信`));
check('タイムラインの返信数', (await card.locator('a.act').innerText()).includes('返信 1'));
await taro.screenshot({ path: `${out}/m4-2-home.png` });

// 存在しない投稿
await taro.goto(base + '/posts/99999999');
await taro.waitForSelector('h1:has-text("ページが見つかりません")');
check('存在しない投稿', true);

// 自分の投稿を詳細画面で削除すると、前の画面に戻る
await taro.goto(base + '/');
await taro.fill('#text-home', `M4 ${stamp} 消す投稿`);
await taro.click('form.composer button[type="submit"]');
const mine = taro.locator(`article.card:has-text("${stamp} 消す投稿")`);
await mine.waitFor();
await mine.locator('a.time').click();
await taro.waitForSelector('article.card.big');
await taro.locator('article.card.big button[aria-label="その他の操作"]').click();
await taro.click('.menu button:has-text("削除")');
await taro.click('dialog button:has-text("削除する")');
await taro.waitForSelector('h1:has-text("ホーム")');
check('詳細画面で削除すると前の画面に戻る', true);

// スマートフォン幅
const phone = await loginAs('taro@example.com', { width: 375, height: 812 });
await phone.locator('article.card').first().waitFor();
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m4-3-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
