// 見やすさ・使いやすさの点検(NF-A11Y-01, 02, 06 / AC-11)。全画面を、スマートフォン幅(375px)と PC 幅、ライトとダークで開き、
// ・横スクロールが出ていないか ・名前のないボタン・リンク・入力欄がないか ・画像に alt があるか
// ・見出し(h1)が 1 つか ・Tab キーで動いたとき、フォーカスの枠が見えるか ・押す部品が小さすぎないか(幅・高さとも 24px 以上)
// ・ダイアログが Esc で閉じ、フォーカスが元のボタンに戻るか
// を調べる。スクリーンショットは output/ に保存。使い方: node a11y-audit.mjs(seed のユーザーで、サーバーと画面が動いていること)
import { chromium } from 'playwright-core';
import { out } from './lib.mjs';

const base = process.argv[2] ?? 'http://localhost:5173';
const problems = [];
const note = (screen, msg) => problems.push(`${screen}: ${msg}`);
const browser = await chromium.launch({ channel: 'msedge' });

async function context(width, scheme) {
  const ctx = await browser.newContext({ viewport: { width, height: width < 500 ? 812 : 900 }, colorScheme: scheme });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => note('script', e.message));
  return page;
}
async function login(page, email) {
  await page.goto(`${base}/login`);
  await page.fill('#login-email', email);
  await page.fill('#login-password', 'password123');
  await page.click('form button[type="submit"]');
  await page.waitForSelector('h1:has-text("ホーム")');
}
const post = (page, text) => page.evaluate(async (text) => {
  const token = decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]);
  const form = new FormData();
  form.append('text', text);
  return (await (await fetch('/api/posts', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: form })).json()).id;
}, text);

async function inspect(page, screen) {
  const r = await page.evaluate(() => {
    const name = (el) => (el.getAttribute('aria-label')
      || (el.getAttribute('aria-labelledby') && document.getElementById(el.getAttribute('aria-labelledby').split(' ')[0])?.innerText)
      || el.innerText || el.getAttribute('title') || el.querySelector('img')?.alt
      || (el.labels && [...el.labels].map((l) => l.innerText).join('')) || el.getAttribute('placeholder') || '').trim();
    const box = (el) => el.getBoundingClientRect();
    const shown = (el) => { const b = box(el); const s = getComputedStyle(el); return b.width > 0 && b.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
    const label = (el) => `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}.${String(el.className).slice(0, 30)}`;
    const controls = [...document.querySelectorAll('button, a[href], input:not([type=hidden]), textarea, select')];
    const unnamed = controls.filter((el) => !name(el)).map(label);
    const noAlt = [...document.querySelectorAll('img')].filter((i) => !i.hasAttribute('alt')).length;
    // 文章の中のリンク(本文の #タグ など)は、小さくてもよい
    const small = controls
      .filter((el) => shown(el) && !el.classList.contains('sr-only') && !el.closest('p, li, .text, .bio, label'))
      .filter((el) => { const b = box(el); return b.width < 24 || b.height < 24; })
      .map((el) => `${label(el)} ${Math.round(box(el).width)}x${Math.round(box(el).height)}`);
    return {
      overflow: document.documentElement.scrollWidth - innerWidth,
      unnamed, noAlt, small,
      h1: document.querySelectorAll('h1').length,
      lang: document.documentElement.lang, title: document.title,
    };
  });
  if (r.overflow > 0) note(screen, `横スクロールが出ている(${r.overflow}px はみ出し)`);
  if (r.unnamed.length) note(screen, `名前のない部品: ${[...new Set(r.unnamed)].join(', ')}`);
  if (r.noAlt) note(screen, `alt のない画像 ${r.noAlt} 枚`);
  if (r.small.length) note(screen, `小さい部品: ${[...new Set(r.small)].join(', ')}`);
  if (r.h1 !== 1) note(screen, `h1 が ${r.h1} 個`);
  if (!r.lang) note(screen, 'html の lang がない');
  if (!r.title) note(screen, 'title がない');
}

// Tab キーを押して進み、フォーカスの枠(outline か box-shadow)が見えるかを確かめる
async function keyboard(page, screen, steps = 25) {
  await page.mouse.click(1, 1);
  const bad = new Set();
  let stuck = 0;
  let last = '';
  for (let i = 0; i < steps; i++) {
    await page.keyboard.press('Tab');
    const info = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el || el === document.body) return null;
      const s = getComputedStyle(el);
      const outline = s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0;
      const shadow = s.boxShadow !== 'none';
      // 画像を選ぶ input(見えない)は、押すボタンが別にある
      const hidden = el.type === 'file' || el.classList.contains('sr-only');
      return { id: `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}.${String(el.className).slice(0, 30)}`, ok: outline || shadow || hidden };
    });
    if (!info) continue;
    if (info.id === last) {
      if (++stuck > 2) break;
    } else {
      stuck = 0;
    }
    last = info.id;
    if (!info.ok) bad.add(info.id);
  }
  if (bad.size) note(screen, `フォーカスの枠が見えない部品: ${[...bad].join(', ')}`);
}

const publicScreens = ['/login', '/signup', '/reset-password', '/nonexistent'];
const loginScreens = ['/', '/search?q=test', '/trends', '/notifications', '/bookmarks', '/users/taro_dev', '/users/taro_dev/following', '/users/hanako', '/settings', '/admin', '/tags/audit'];

for (const [width, scheme] of [[375, 'light'], [375, 'dark'], [1280, 'light'], [1280, 'dark']]) {
  const tag = `${width}px/${scheme}`;
  const anon = await context(width, scheme);
  for (const path of publicScreens) {
    await anon.goto(base + path);
    await anon.waitForSelector('h1');
    await inspect(anon, `${path} (${tag})`);
    if (width === 375 && scheme === 'light') await keyboard(anon, `${path} (${tag})`, 12);
  }
  const page = await context(width, scheme);
  await login(page, 'taro@example.com');
  const id = await post(page, `点検用の投稿 #audit ${Date.now()}`);
  await post(page, '点検用の2件目');
  for (const path of [...loginScreens, `/posts/${id}`]) {
    await page.goto(base + path);
    await page.waitForSelector('h1');
    await page.waitForTimeout(400);
    await inspect(page, `${path} (${tag})`);
    if (width === 375 && scheme === 'light') await keyboard(page, `${path} (${tag})`);
    if (scheme === 'dark' || width === 375) await page.screenshot({ path: `${out}/audit-${path.replace(/\W+/g, '_')}-${width}-${scheme}.png` });
  }
  // ダイアログ: Esc で閉じる・フォーカスが元のボタンに戻る
  if (width === 375 && scheme === 'light') {
    await page.goto(`${base}/`);
    await page.waitForSelector('article.card');
    await page.locator('article.card button[aria-label="その他の操作"]').first().click();
    await page.locator('.menu button:has-text("編集")').click();
    await page.waitForSelector('dialog[open]');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    if (await page.locator('dialog[open]').count()) note('ダイアログ', 'Esc で閉じない');
    const back = await page.evaluate(() => document.activeElement?.getAttribute('aria-label'));
    if (back !== 'その他の操作') note('ダイアログ', `閉じたあと、フォーカスが元のボタンに戻らない(${back})`);
  }
}
await browser.close();
console.log(problems.length ? `問題 ${problems.length} 件\n${[...new Set(problems)].join('\n')}` : '問題なし');
process.exit(problems.length ? 1 : 0);
