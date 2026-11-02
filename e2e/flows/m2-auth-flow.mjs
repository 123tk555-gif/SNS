import { chromium } from 'playwright-core';

const base = process.argv[2] ?? 'http://localhost:5173';
import { out } from '../lib.mjs';
const email = `e2e-${Date.now()}@example.com`;
const handle = `e2e_${String(Date.now()).slice(-6)}`;
const results = [];
const errors = [];
const check = (name, ok, extra = '') => results.push(`${ok ? 'OK ' : 'NG '} ${name}${extra ? ` (${extra})` : ''}`);

// メール確認用サーバー(Mailpit)の URL。公開用の構成を手元で試すときは MAILPIT_URL で変える
const mailpit = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
// 手元の公開用構成(自分で作った証明書)を試すときは IGNORE_HTTPS_ERRORS=1
const ignoreHTTPSErrors = process.env.IGNORE_HTTPS_ERRORS === '1';

async function latestToken(to, path) {
  for (let i = 0; i < 20; i += 1) {
    const list = await (await fetch(`${mailpit}/api/v1/messages`)).json();
    const message = list.messages.find((m) => m.To.some((t) => t.Address === to) && m.Subject.includes(path === 'verify-email' ? '確認' : '再設定'));
    if (message) {
      const full = await (await fetch(`${mailpit}/api/v1/message/${message.ID}`)).json();
      return full.Text.match(/token=([A-Za-z0-9_-]+)/)[1];
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error('メールが届きません');
}

const browser = await chromium.launch({ channel: 'msedge' });
const page = await browser.newPage({ ignoreHTTPSErrors, viewport: { width: 1280, height: 900 } });
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
const title = () => page.textContent('h1');

// 未ログインでホームを開くとログイン画面へ
await page.goto(base + '/');
await page.waitForSelector('h1:has-text("ログイン")');
check('未ログインはログイン画面へ', page.url().endsWith('/login'));
await page.screenshot({ path: `${out}/auth-1-login.png` });

// 新規登録
await page.click('text=アカウントを作成する');
await page.fill('#signup-email', 'bad');
await page.fill('#signup-password', 'short');
await page.click('button:has-text("登録する")');
check('登録の入力チェック', (await page.textContent('#signup-email-error')).includes('メールアドレスの形'));
await page.fill('#signup-email', email);
await page.fill('#signup-password', 'password123');
await page.click('button:has-text("登録する")');
await page.waitForSelector('h1:has-text("確認メールを送りました")');
check('登録すると確認メールの案内', true);

// メールのリンクを開く → 初期設定
const verifyToken = await latestToken(email, 'verify-email');
await page.goto(`${base}/verify-email?token=${verifyToken}`);
await page.waitForSelector('h1:has-text("プロフィールを設定しましょう")');
check('確認リンクで初期設定へ', page.url().endsWith('/onboarding'));
await page.fill('#onb-handle', 'admin');
await page.waitForTimeout(800);
check('予約語のユーザーIDは使えない', (await page.textContent('#onb-handle-error')).includes('使えません'));
await page.fill('#onb-handle', handle);
await page.waitForSelector('text=使えます');
check('使えるユーザーIDの表示', true);
await page.fill('#onb-name', 'E2E さん');
await page.screenshot({ path: `${out}/auth-2-onboarding.png` });
await page.click('button:has-text("はじめる")');
await page.waitForSelector('h1:has-text("ホーム")');
// M3 からのホームはタイムラインなので、メニューの「プロフィール」のリンク先で、決めたユーザーIDを確かめる
check('初期設定でホームへ', (await page.getAttribute('nav a.nav-item:has-text("プロフィール")', 'href')) === `/users/${handle}`);

// 再読み込みしてもログインが続く(セッションはデータベース)
await page.reload();
await page.waitForSelector('h1:has-text("ホーム")');
check('再読み込みしてもログインが続く', true);
await page.screenshot({ path: `${out}/auth-3-home.png` });

// ログアウト → ログイン
// M3 からは、ログアウトは設定画面にある
await page.click('nav a:has-text("設定")');
await page.click('button:has-text("ログアウト")');
await page.waitForSelector('text=ログアウトしました');
check('ログアウト', page.url().endsWith('/login'));
await page.fill('#login-email', email);
await page.fill('#login-password', 'wrong-password');
await page.click('button:has-text("ログイン")');
await page.waitForSelector('text=メールアドレスまたはパスワードが違います');
check('パスワード違いの文', true);
await page.fill('#login-password', 'password123');
await page.click('button:has-text("ログイン")');
await page.waitForSelector('h1:has-text("ホーム")');
check('ログイン', true);

// ログイン中にログイン画面を開くとホームへ
await page.goto(base + '/login');
await page.waitForSelector('h1:has-text("ホーム")');
check('ログイン中はログイン画面に戻らない', true);

// パスワード再設定(別のブラウザで)
const other = await browser.newPage({ ignoreHTTPSErrors });
await other.goto(base + '/reset-password');
await other.fill('#reset-email', email);
await other.click('button:has-text("送信する")');
await other.waitForSelector('h1:has-text("メールを送りました")');
const resetToken = await latestToken(email, 'reset-password');
await other.goto(`${base}/reset-password?token=${resetToken}`);
await other.fill('#new-password', 'new-password-9');
await other.fill('#new-password-confirm', 'different-1');
await other.click('button:has-text("設定する")');
check('確認用のパスワードが違う', (await other.textContent('#new-password-confirm-error')).includes('一致しません'));
await other.fill('#new-password-confirm', 'new-password-9');
await other.click('button:has-text("設定する")');
await other.waitForSelector('text=パスワードを変更しました');
check('パスワード再設定', true);
await other.fill('#login-email', email);
await other.fill('#login-password', 'new-password-9');
await other.click('button:has-text("ログイン")');
await other.waitForSelector('h1:has-text("ホーム")');
check('新しいパスワードでログイン', true);

// 最初のブラウザのログインは、パスワード再設定で消えている
await page.reload();
await page.waitForSelector('h1:has-text("ログイン")');
check('再設定すると他の端末はログアウト', true);

// 存在しない画面
await page.goto(base + '/no-such-page');
await page.waitForSelector('h1');
check('存在しない画面', (await title()).includes('見つかりません'));

// スマートフォンの幅
const phone = await browser.newPage({ ignoreHTTPSErrors, viewport: { width: 375, height: 812 } });
await phone.goto(base + '/login');
await phone.waitForSelector('h1');
check('スマホ幅で横スクロールなし', await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await phone.screenshot({ path: `${out}/auth-4-phone.png` });

await browser.close();
console.log(results.join('\n'));
console.log(errors.length ? `エラー:\n${errors.join('\n')}` : 'エラーなし');
