import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out } from '../lib.mjs';
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

const admin = await loginAs('taro@example.com'); // seed の taro_dev は管理者
const hanako = await loginAs('hanako@example.com');
const ken = await loginAs('ken@example.com');
const target = `管理者テスト投稿${stamp}`;
await apiPost(hanako, target);
await apiPost(ken, `けんの投稿${stamp}`);

// 一般ユーザーには、管理者画面のメニューも画面もない
check('一般ユーザーのメニューに管理者画面がない', (await hanako.locator('nav a.nav-item:has-text("管理者画面")').count()) === 0);
await hanako.goto(base + '/admin');
await hanako.waitForSelector('text=ページが見つかりません');
check('一般ユーザーが /admin を開くと「ページが見つかりません」', true);

// 管理者: 投稿の一覧
await admin.click('nav a.nav-item:has-text("管理者画面")');
await admin.waitForSelector('h1:has-text("管理者画面")');
await admin.waitForSelector('.admin-row:not(.head)');
check('投稿タブが選ばれている', (await admin.getAttribute('a.tab:has-text("投稿")', 'aria-current')) === 'page');
check('投稿の一覧に今回の投稿', (await admin.locator(`.admin-row:has-text("${target}")`).count()) === 1);
check('状態は「公開中」', (await admin.locator(`.admin-row:has-text("${target}") .status`).innerText()) === '公開中');
await admin.screenshot({ path: `${out}/m9-1-posts.png` });

// 検索
await admin.fill('#admin-q', `けんの投稿${stamp}`);
await admin.press('#admin-q', 'Enter');
await admin.waitForFunction(() => document.querySelectorAll('.admin-row:not(.head)').length === 1);
check('本文で検索すると1件', (await admin.locator('.admin-row:not(.head)').innerText()).includes('@ken_eng'));
check('URL に検索語が入る', admin.url().includes('q='));
await admin.fill('#admin-q', `存在しない${stamp}`);
await admin.press('#admin-q', 'Enter');
await admin.waitForSelector('text=該当するものはありません');
check('見つからないときの表示', (await admin.locator('.pager span').innerText()).includes('0件'));
await admin.fill('#admin-q', target);
await admin.press('#admin-q', 'Enter');
await admin.waitForFunction(() => document.querySelectorAll('.admin-row:not(.head)').length === 1);

// 非表示(D-06)
const hanakoHome = hanako.locator(`article.card:has-text("${target}")`);
await hanako.goto(base + '/');
await hanakoHome.waitFor();
await admin.locator('.admin-row:not(.head) button:has-text("非表示にする")').click();
await admin.waitForSelector('dialog[open]');
check('D-06 の題名と説明', (await admin.textContent('dialog[open]')).includes('この投稿を非表示にしますか?') && (await admin.textContent('dialog[open]')).includes('投稿者以外には表示されなくなります'));
check('D-06 は最初「やめる」を選んでいる', await admin.evaluate(() => document.activeElement?.id === 'admin-cancel'));
await admin.screenshot({ path: `${out}/m9-2-confirm.png` });
await admin.click('#admin-cancel');
check('やめると状態は変わらない', (await admin.locator('.admin-row:not(.head) .status').innerText()) === '公開中');
await admin.locator('.admin-row:not(.head) button:has-text("非表示にする")').click();
await admin.click('dialog[open] button.btn-danger');
check('非表示にしたお知らせ', await toast(admin, '投稿を非表示にしました'));
check('表の状態がすぐ「非表示」になる', (await admin.locator('.admin-row:not(.head) .status').innerText()) === '非表示');
check('ボタンが「表示に戻す」になる', await admin.locator('.admin-row:not(.head) button:has-text("表示に戻す")').isVisible());
// 開いているホームから、再読み込みなしで消える(SSE)
await hanako.waitForFunction((t) => !document.body.innerText.includes(t), target, { timeout: 5000 });
check('開いている画面から、再読み込みなしで消える', true);
// 本人のプロフィールには印つきで残る
await hanako.goto(base + '/users/hanako');
await hanako.waitForSelector('article.card');
check('本人の一覧には「管理者により非表示になりました」', (await hanako.locator(`article.card:has-text("${target}")`).innerText()).includes('管理者により非表示になりました'));
// 他の人には出ない
await ken.goto(base + '/users/hanako');
await ken.waitForSelector('section.profile');
await ken.waitForTimeout(500);
check('他の人のプロフィールには出ない', (await ken.locator(`article.card:has-text("${target}")`).count()) === 0);
// 戻す
await admin.locator('.admin-row:not(.head) button:has-text("表示に戻す")').click();
check('表示に戻したお知らせ', await toast(admin, '投稿を表示に戻しました'));

// ユーザータブ
await admin.click('a.tab:has-text("ユーザー")');
await admin.waitForSelector('.admin-row.users:not(.head)');
check('ユーザーの表に投稿数', (await admin.locator('.admin-row.users:has-text("@hanako")').innerText()).includes('投稿 1件'));
const adminRow = admin.locator('.admin-row.users:has-text("@taro_dev")');
check('自分(管理者)の行に操作のボタンがない', (await adminRow.locator('button').count()) === 0);
check('管理者の印', await adminRow.locator('.chip:has-text("管理者")').isVisible());
await admin.fill('#admin-q', 'ken@');
await admin.press('#admin-q', 'Enter');
await admin.waitForFunction(() => document.querySelectorAll('.admin-row.users:not(.head)').length === 1);
check('メールアドレスで検索', (await admin.locator('.admin-row.users:not(.head)').innerText()).includes('@ken_eng'));

// 利用停止
await admin.locator('.admin-row.users:not(.head) button:has-text("利用停止にする")').click();
check('D-06 ユーザー停止の文', (await admin.textContent('dialog[open]')).includes('@ken_eng さんを利用停止にしますか?') && (await admin.textContent('dialog[open]')).includes('すぐにログアウトされ'));
await admin.click('dialog[open] button.btn-danger');
check('利用停止にしたお知らせ', await toast(admin, '利用停止にしました'));
check('状態が「利用停止中」', (await admin.locator('.admin-row.users:not(.head) .status').innerText()) === '利用停止中');
await admin.screenshot({ path: `${out}/m9-3-users.png` });
// けんの画面は次の操作でログアウトされる
await ken.goto(base + '/');
await ken.waitForSelector('text=ログイン', { timeout: 5000 });
check('停止されたユーザーはログアウトされる', ken.url().includes('/login'));
await admin.locator('.admin-row.users:not(.head) button:has-text("停止を解除")').click();
check('停止を解除したお知らせ', await toast(admin, '利用停止を解除しました'));

// スマートフォン幅
const phone = await loginAs('taro@example.com', { width: 375, height: 812 });
await phone.goto(base + '/admin');
await phone.waitForSelector('.admin-row:not(.head)');
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/m9-4-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
