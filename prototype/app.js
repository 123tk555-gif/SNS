'use strict';

// SNSタイムラインアプリ プロトタイプ
// 画面仕様定義書(docs/画面仕様定義書.md)の画面と操作を、サーバーなしで再現する。
// データはこのファイルの中だけにあり、ページを読み込み直すと最初の状態に戻る。
// 画面は URL の # の後ろ(例: #/posts/11)で切り替える。ブラウザの「戻る」も使える。

const ME = 'taro_dev';
const MINUTE = 60 * 1000;
const MAX_TEXT = 1000;
const MAX_IMAGES = 10;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const RESERVED_HANDLES = ['admin', 'administrator', 'root', 'system', 'support', 'official'];
const INCOMING_INTERVAL = 20 * 1000;

const ago = (minutes) => Date.now() - minutes * MINUTE;
const emptyDraft = () => ({ text: '', images: [], error: '' });

// ---- 最初のデータ ----
// users のキーは内部の ID。画面に出す @ユーザーID は handle(初期設定で変えられる)
const state = {
  loggedIn: false,
  afterLogin: '#/',
  route: { name: 'login' },
  theme: 'system',
  users: {
    taro_dev: { handle: 'taro_dev', name: 'たろう', bio: 'Java と React を勉強中です。#SpringBoot でアプリを作っています', color: '#1d4ed8', followers: 30, following: 12, joined: '2026年10月', role: 'ADMIN', email: 'taro@example.com' },
    hanako: { handle: 'hanako', name: 'はなこ', bio: 'Web デザインとカフェめぐりが好きです #カフェ巡り', color: '#b4410c', followers: 128, following: 96, joined: '2026年10月', role: 'USER', email: 'hanako@example.com' },
    ken_eng: { handle: 'ken_eng', name: 'けん', bio: 'インフラエンジニア。AWS と Docker の話をします', color: '#047857', followers: 342, following: 51, joined: '2026年10月', role: 'USER', email: 'ken@example.com' },
    mika: { handle: 'mika', name: 'みか', bio: '週末に写真を撮っています #写真', color: '#6d28d9', followers: 87, following: 40, joined: '2026年10月', role: 'USER', email: 'mika@example.com' },
    yuto: { handle: 'yuto', name: 'ゆうと', bio: '大学で情報工学を専攻しています', color: '#be185d', followers: 19, following: 33, joined: '2026年10月', role: 'USER', email: 'yuto@example.com' },
  },
  posts: [
    { id: 15, author: 'hanako', text: '新しいカフェを見つけました。窓際の席が気持ちいい #カフェ巡り', at: ago(3), likes: 12, images: [{ alt: '窓際の席に置かれたコーヒーカップ', tone: '#e9dccb' }] },
    { id: 14, author: 'ken_eng', text: 'Docker Compose で PostgreSQL 18 を立ち上げました。healthcheck を入れておくと起動順で困らなくなります #Docker #PostgreSQL', at: ago(25), likes: 8, liked: true },
    { id: 13, author: 'hanako', text: 'どういたしまして!次は画像のアップロードですね', at: ago(50), likes: 2, replyTo: 11 },
    { id: 12, author: 'yuto', text: '参考になります。自分も Spring Boot をやってみます', at: ago(100), likes: 1, replyTo: 11 },
    { id: 11, author: 'taro_dev', text: '今日は #SpringBoot で REST API を作りました。@hanako さん、教えてくれてありがとう!', at: ago(120), likes: 5 },
    { id: 10, author: 'mika', text: '夕焼けがきれいでした #写真', at: ago(300), likes: 31, images: [{ alt: 'オレンジ色の夕焼けと川', tone: '#f2c29b' }, { alt: '夕焼けに染まる街並み', tone: '#e7a98b' }] },
    { id: 9, author: 'yuto', text: '期末試験が終わりました。これからポートフォリオ作りに集中します', at: ago(1200), likes: 3 },
    { id: 8, author: 'hanako', text: '#カフェ巡り 3軒目。チーズケーキが絶品でした', at: ago(900), likes: 15, bookmarked: true },
    { id: 7, author: 'ken_eng', text: 'AWS の料金を抑えるコツをまとめています。料金計算ツールが便利です https://calculator.aws/ #AWS', at: ago(2900), likes: 6 },
    { id: 6, author: 'mika', text: '#写真 の練習で、毎朝同じ場所を撮っています', at: ago(4400), likes: 20, images: [{ alt: '朝の公園の並木道', tone: '#cfe3d4' }] },
  ],
  notifications: [
    { id: 5, type: 'LIKE', actor: 'hanako', post: 11, at: ago(40), read: false },
    { id: 4, type: 'REPLY', actor: 'hanako', post: 13, at: ago(50), read: false },
    { id: 3, type: 'FOLLOW', actor: 'yuto', at: ago(200), read: false },
    { id: 2, type: 'LIKE', actor: 'mika', post: 11, at: ago(300), read: true },
  ],
  following: ['hanako', 'ken_eng'],
  followers: ['hanako', 'yuto', 'mika'],
  muted: [],
  blocked: [],
  suspended: [],
  nextPostId: 16,
  nextNotificationId: 6,
  incomingIndex: 0,
  newCount: 0,
  menuFor: null,
  drafts: { home: emptyDraft(), reply: emptyDraft(), dialog: emptyDraft() },
  bookmarksShown: [],
  signupSent: false,
  pendingRender: false,
};

// 他のユーザーから届く(ことにする)投稿。20秒ごとに 1 つずつ届く
const INCOMING = [
  { author: 'yuto', text: '研究室のゼミが終わりました。今日は #React の勉強をします' },
  { author: 'mika', text: '朝の公園を撮りに行ってきました #写真', images: [{ alt: '朝もやのかかった公園の池', tone: '#d5e4ea' }] },
  { author: 'hanako', text: '@taro_dev 画像アップロードの実装、どうでした?' },
  { author: 'ken_eng', text: 'EC2 の t4g.micro でも Spring Boot が動きました #AWS #Docker' },
];
const LIKERS = ['yuto', 'mika', 'ken_eng'];

// ---- 小さな道具 ----
const $ = (selector) => document.querySelector(selector);
const app = $('#app');

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// 見た目の 1 文字を 1 文字として数える(絵文字も 1 文字)
const charCount = (text) => [...text.trim()].length;
const normalize = (text) => text.normalize('NFKC').toLowerCase();

function relTime(at) {
  const minutes = Math.floor((Date.now() - at) / MINUTE);
  if (minutes < 1) return 'たった今';
  if (minutes < 60) return `${minutes}分前`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}時間前`;
  const d = new Date(at);
  const md = `${d.getMonth() + 1}月${d.getDate()}日`;
  return d.getFullYear() === new Date().getFullYear() ? md : `${d.getFullYear()}年${md}`;
}

function fullTime(at) {
  const d = new Date(at);
  const hm = `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${hm}`;
}

const userKeyByHandle = (handle) =>
  Object.keys(state.users).find((key) => state.users[key].handle.toLowerCase() === String(handle).toLowerCase());
const me = () => state.users[ME];
const findPost = (id) => state.posts.find((p) => p.id === id);
const isAdmin = () => me().role === 'ADMIN';

// 本文から抜き出すルール(機能定義書 3.3)
const TAG_RE = /#([\p{L}\p{N}_ー]{1,50})/gu;
const RICH_RE = /(#[\p{L}\p{N}_ー]{1,50}|@[A-Za-z0-9_]{3,15}|https?:\/\/[^\s]+)/gu;

function extractTags(text) {
  const tags = new Set();
  for (const m of text.matchAll(TAG_RE)) {
    const before = text[m.index - 1];
    if (before && /[A-Za-z0-9]/.test(before)) continue;
    if (/^\d+$/.test(m[1])) continue;
    tags.add(normalize(m[1]));
  }
  return [...tags];
}

function richText(text) {
  let html = '';
  let last = 0;
  for (const m of text.matchAll(RICH_RE)) {
    const token = m[0];
    html += esc(text.slice(last, m.index));
    if (token[0] === '#' && !/^#\d+$/.test(token)) {
      html += `<a href="#/tags/${encodeURIComponent(normalize(token.slice(1)))}">${esc(token)}</a>`;
    } else if (token[0] === '@' && userKeyByHandle(token.slice(1))) {
      html += `<a href="#/users/${esc(state.users[userKeyByHandle(token.slice(1))].handle)}">${esc(token)}</a>`;
    } else if (token.startsWith('http')) {
      html += `<a href="${esc(token)}" target="_blank" rel="noopener noreferrer">${esc(token)}<span class="sr-only">(新しいタブで開きます)</span></a>`;
    } else {
      html += esc(token);
    }
    last = m.index + token.length;
  }
  return html + esc(text.slice(last));
}

// ---- どの投稿が見えるか(機能定義書 1.6) ----
const visibleBase = (p) => !p.deleted && !p.hidden && !state.suspended.includes(p.author);
const visible = (p) => visibleBase(p) && !state.blocked.includes(p.author) && !state.muted.includes(p.author);
const replyCount = (id) => state.posts.filter((p) => p.replyTo === id && visibleBase(p)).length;

function timeline(tab) {
  return state.posts.filter(
    (p) => !p.replyTo && visible(p) && (tab === 'all' || p.author === ME || state.following.includes(p.author)),
  );
}

function trendTags(limit = 10) {
  const counts = new Map();
  for (const p of state.posts) {
    if (!visibleBase(p) || p.at < ago(1440)) continue;
    for (const tag of extractTags(p.text)) {
      const entry = counts.get(tag) ?? { name: tag, count: 0, last: 0 };
      entry.count += 1;
      entry.last = Math.max(entry.last, p.at);
      counts.set(tag, entry);
    }
  }
  return [...counts.values()].sort((a, b) => b.count - a.count || b.last - a.last).slice(0, limit);
}

const popularPosts = () =>
  state.posts
    .filter((p) => !p.replyTo && visible(p) && p.at >= ago(1440) && p.likes > 0)
    .sort((a, b) => b.likes - a.likes || b.at - a.at)
    .slice(0, 10);

function matchesAll(text, query) {
  const target = normalize(text);
  return query.split(/[\s　]+/).filter(Boolean).every((word) => target.includes(normalize(word)));
}

const unreadCount = () => state.notifications.filter((n) => !n.read && notificationVisible(n)).length;
function notificationVisible(n) {
  if (state.suspended.includes(n.actor)) return false;
  if (n.post) {
    const p = findPost(n.post);
    if (!p || !visibleBase(p)) return false;
  }
  return true;
}

// ---- 部品 ----
const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  bell: '<path d="M6 9a6 6 0 0 1 12 0c0 6 3 8 3 8H3s3-2 3-8"/><path d="M10 21a2 2 0 0 0 4 0"/>',
  hash: '<path d="M4 9h16M4 15h16M10 3 8 21M16 3l-2 18"/>',
  bookmark: '<path d="M6 3h12v18l-6-4-6 4z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  shield: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/>',
  heart: '<path d="M12 20s-7-4.5-9-9a4.5 4.5 0 0 1 9-3 4.5 4.5 0 0 1 9 3c-2 4.5-9 9-9 9z"/>',
  reply: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5A8 8 0 1 1 21 12z"/>',
  more: '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 17-5-5-9 8"/>',
  pen: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
  back: '<path d="M19 12H5M11 5l-7 7 7 7"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  user_plus: '<circle cx="10" cy="8" r="4"/><path d="M2 21c0-4 4-6 8-6s8 2 8 6M19 8v6M16 11h6"/>',
  at: '<circle cx="12" cy="12" r="4"/><path d="M16 12v1.5a2.5 2.5 0 0 0 5 0V12a9 9 0 1 0-3.5 7.1"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>',
};

function icon(name, { size = 24, filled = false, label = '' } = {}) {
  const a11y = label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"';
  const fill = filled ? 'currentColor' : 'none';
  return `<svg ${a11y} width="${size}" height="${size}" viewBox="0 0 24 24" fill="${fill}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg>`;
}

function avatar(key, size = '') {
  const u = state.users[key];
  const style = u.avatarUrl
    ? `background: ${u.color} url('${esc(u.avatarUrl)}') center / cover`
    : `background: ${u.color}`;
  const initial = u.avatarUrl ? '' : esc([...u.name][0] ?? '?');
  return `<a class="avatar ${size}" href="#/users/${esc(u.handle)}" style="${style}" aria-label="${esc(u.name)}さんのプロフィール">${initial}</a>`;
}

function imageBox(post, image, index, total) {
  const inner = image.url
    ? `<img src="${esc(image.url)}" alt="">`
    : `<span class="ph" style="background: ${image.tone}">${esc(image.alt || '画像')}</span>`;
  const more = index === 3 && total > 4 ? `<span class="more" aria-hidden="true">+${total - 4}枚</span>` : '';
  const label = `画像 ${index + 1} / ${total} を拡大: ${image.alt || '説明なし'}`;
  return `<button class="img" type="button" data-action="view-image" data-id="${post.id}" data-index="${index}" data-key="img-${post.id}-${index}" aria-label="${esc(label)}">${inner}${more}</button>`;
}

function postCard(p, { big = false, showComposer = false } = {}) {
  if (!visibleBase(p) && !(p.hidden && p.author === ME && !p.deleted)) {
    return `<div class="card"><p class="text">この投稿は削除されました</p></div>`;
  }
  const u = state.users[p.author];
  const mine = p.author === ME;
  const images = p.images ?? [];
  const shown = images.slice(0, 4);
  let replyLine = '';
  if (p.replyTo) {
    const parent = findPost(p.replyTo);
    replyLine = parent && visibleBase(parent)
      ? `<p class="reply-to"><a href="#/posts/${parent.id}">@${esc(state.users[parent.author].handle)} さんへの返信</a></p>`
      : '<p class="reply-to">削除された投稿への返信</p>';
  }
  const menuOpen = state.menuFor === p.id;
  const muted = state.muted.includes(p.author);
  const menu = menuOpen
    ? `<div class="menu" role="group" aria-label="投稿のメニュー">${
        mine
          ? `<button type="button" data-action="edit" data-id="${p.id}" data-key="edit-${p.id}">編集</button>
             <button type="button" class="danger" data-action="delete" data-id="${p.id}" data-key="delete-${p.id}">削除</button>`
          : `<button type="button" data-action="mute" data-user="${p.author}" data-key="mute-${p.id}">${muted ? `@${esc(u.handle)} さんのミュートを解除` : `@${esc(u.handle)} さんをミュート`}</button>
             <button type="button" class="danger" data-action="block" data-user="${p.author}" data-key="block-${p.id}">@${esc(u.handle)} さんをブロック</button>`
      }</div>`
    : '';
  const replies = replyCount(p.id);

  return `
  <article class="card${p.isNew ? ' is-new' : ''}${big ? ' big' : ''}" aria-label="${esc(u.name)}さんの投稿">
    ${avatar(p.author)}
    <div class="card-body">
      ${replyLine}
      <div class="meta">
        <a class="name" href="#/users/${esc(u.handle)}">${esc(u.name)}</a>
        <span class="handle">@${esc(u.handle)}</span>
        <a class="time" href="#/posts/${p.id}" title="${fullTime(p.at)}">${big ? fullTime(p.at) : relTime(p.at)}</a>
        ${p.edited ? '<span class="edited">(編集済み)</span>' : ''}
        ${p.isNew ? '<span class="label-new">新着</span>' : ''}
      </div>
      ${p.hidden ? '<p class="notice-hidden">管理者により非表示になりました</p>' : ''}
      ${p.text ? `<p class="text">${richText(p.text)}</p>` : ''}
      ${shown.length ? `<div class="imgs n${Math.min(shown.length, 2)}">${shown.map((im, i) => imageBox(p, im, i, images.length)).join('')}</div>` : ''}
      <div class="actions">
        <a class="act" href="#/posts/${p.id}?reply" aria-label="返信 ${replies}件。返信する">${icon('reply', { size: 20 })}<span aria-hidden="true">返信 ${replies}</span></a>
        <button class="act like" type="button" aria-pressed="${p.liked ? 'true' : 'false'}" data-action="like" data-id="${p.id}" data-key="like-${p.id}">
          ${icon('heart', { size: 20, filled: p.liked })}<span>${p.liked ? 'いいね済み' : 'いいね'} ${p.likes}</span>
        </button>
        <button class="act bm" type="button" aria-pressed="${p.bookmarked ? 'true' : 'false'}" data-action="bookmark" data-id="${p.id}" data-key="bm-${p.id}">
          ${icon('bookmark', { size: 20, filled: p.bookmarked })}<span>${p.bookmarked ? 'ブックマーク済み' : 'ブックマーク'}</span>
        </button>
        <div class="menu-wrap">
          <button class="act" type="button" aria-label="その他の操作" aria-expanded="${menuOpen}" data-action="menu" data-id="${p.id}" data-key="more-${p.id}">${icon('more', { size: 20 })}</button>
          ${menu}
        </div>
      </div>
    </div>
  </article>
  ${showComposer ? composer('reply', { replyTo: p }) : ''}`;
}

function postList(posts, emptyHtml) {
  if (!posts.length) return `<div class="empty">${emptyHtml}</div>`;
  return posts.map((p) => postCard(p)).join('') + '<p class="list-end">これ以上はありません</p>';
}

function followButton(key) {
  if (key === ME) return '';
  const on = state.following.includes(key);
  const handle = esc(state.users[key].handle);
  return on
    ? `<button class="btn btn-outline" type="button" data-action="follow" data-user="${key}" data-key="follow-${key}" aria-label="@${handle} さんのフォローを解除"
         onmouseenter="this.firstChild.textContent='フォローを解除'" onmouseleave="this.firstChild.textContent='フォロー中'"
         onfocus="this.firstChild.textContent='フォローを解除'" onblur="this.firstChild.textContent='フォロー中'">フォロー中</button>`
    : `<button class="btn btn-primary" type="button" data-action="follow" data-user="${key}" data-key="follow-${key}" aria-label="@${handle} さんをフォローする">フォローする</button>`;
}

function userRow(key, { action = '' } = {}) {
  const u = state.users[key];
  return `
  <div class="user-row">
    ${avatar(key)}
    <div class="info">
      <a class="name" href="#/users/${esc(u.handle)}">${esc(u.name)}</a>
      <span class="handle">@${esc(u.handle)}</span>
      ${state.followers.includes(key) && key !== ME ? '<span class="chip">フォローされています</span>' : ''}
      ${state.muted.includes(key) ? '<span class="chip">ミュート中</span>' : ''}
      <p class="bio">${esc(u.bio)}</p>
    </div>
    ${action || followButton(key)}
  </div>`;
}

function composer(kind, { replyTo = null } = {}) {
  const draft = state.drafts[kind];
  const placeholder = replyTo ? '返信をポスト' : 'いまどうしてる?';
  const thumbs = draft.images
    .map(
      (im, i) => `
      <div class="thumb">
        <div class="pic">
          <img src="${esc(im.url)}" alt="">
          <button class="remove" type="button" data-action="remove-image" data-kind="${kind}" data-index="${i}" aria-label="画像 ${i + 1} を取り消す">${icon('close', { size: 18 })}</button>
        </div>
        <label class="sr-only" for="alt-${kind}-${i}">画像 ${i + 1} の説明(代替テキスト)</label>
        <input type="text" id="alt-${kind}-${i}" data-input="alt" data-kind="${kind}" data-index="${i}" placeholder="画像の説明を追加" value="${esc(im.alt)}">
      </div>`,
    )
    .join('');
  return `
  <form class="composer${kind === 'home' ? ' hide-sm' : ''}" data-form="post" data-kind="${kind}" ${replyTo ? `data-reply-to="${replyTo.id}"` : ''} novalidate>
    ${avatar(ME)}
    <div class="grow">
      ${replyTo ? `<p class="reply-note">@${esc(state.users[replyTo.author].handle)} さんへの返信</p>` : ''}
      <label class="sr-only" for="text-${kind}">${replyTo ? '返信の本文' : '投稿の本文'}</label>
      <textarea id="text-${kind}" data-key="text-${kind}" data-input="draft" data-kind="${kind}" placeholder="${placeholder}" aria-describedby="counter-${kind} error-${kind}">${esc(draft.text)}</textarea>
      ${draft.images.length ? `<div class="thumbs">${thumbs}</div>` : ''}
      <p class="field-error" id="error-${kind}" ${draft.error ? '' : 'hidden'}>${esc(draft.error)}</p>
      <div class="composer-foot">
        <input class="sr-only" type="file" id="file-${kind}" tabindex="-1" accept="${IMAGE_TYPES.join(',')}" multiple data-change="images" data-kind="${kind}">
        <button class="icon-btn" type="button" data-action="pick-images" data-kind="${kind}" data-key="pick-${kind}" aria-label="画像を追加(最大10枚。いまの枚数 ${draft.images.length}枚)">${icon('image')}</button>
        <span class="spacer"></span>
        <span class="counter" id="counter-${kind}"></span>
        <button class="btn btn-primary" type="submit" id="submit-${kind}" data-key="submit-${kind}">${replyTo ? '返信する' : '投稿する'}</button>
      </div>
    </div>
  </form>`;
}

// 本文の残り文字数とボタンの状態は、描画のあとにここで入れる
// (入力のたびに画面全体を書き直すと、入力中の位置が失われるため)
function updateComposer(kind) {
  const draft = state.drafts[kind];
  const counter = document.getElementById(`counter-${kind}`);
  const submit = document.getElementById(`submit-${kind}`);
  if (!counter || !submit) return;
  const left = MAX_TEXT - charCount(draft.text);
  counter.className = 'counter' + (left < 0 ? ' over' : left < 20 ? ' warn' : '');
  counter.textContent = left < 0 ? `${-left} 文字オーバー` : `残り ${left} 文字`;
  submit.disabled = left < 0 || (charCount(draft.text) === 0 && draft.images.length === 0);
}

// ---- 画面 ----
const NAV = [
  { key: 'home', label: 'ホーム', href: '#/', icon: 'home' },
  { key: 'search', label: '検索', href: '#/search', icon: 'search' },
  { key: 'notifications', label: '通知', href: '#/notifications', icon: 'bell' },
  { key: 'trends', label: 'トレンド', href: '#/trends', icon: 'hash', hideSm: true },
  { key: 'bookmarks', label: 'ブックマーク', href: '#/bookmarks', icon: 'bookmark', hideSm: true },
  { key: 'profile', label: 'プロフィール', href: () => `#/users/${me().handle}`, icon: 'user' },
  { key: 'settings', label: '設定', href: '#/settings', icon: 'settings', hideSm: true },
  { key: 'admin', label: '管理者画面', href: '#/admin', icon: 'shield', hideSm: true, adminOnly: true },
];

function currentNav() {
  const r = state.route;
  if (r.name === 'tag') return 'trends';
  if ((r.name === 'profile' || r.name === 'follows') && userKeyByHandle(r.handle) === ME) return 'profile';
  return r.name;
}

function layout(title, content, { back = false, sub = '', extra = '' } = {}) {
  const current = currentNav();
  const unread = unreadCount();
  const nav = NAV.filter((n) => !n.adminOnly || isAdmin())
    .map((n) => {
      const href = typeof n.href === 'function' ? n.href() : n.href;
      const badge = n.key === 'notifications' && unread
        ? `<span class="badge" aria-hidden="true">${unread > 99 ? '99+' : unread}</span><span class="sr-only">(未読 ${unread}件)</span>`
        : '';
      return `<a class="nav-item${n.hideSm ? ' hide-sm' : ''}" href="${href}" ${current === n.key ? 'aria-current="page"' : ''}>
        ${icon(n.icon, { size: 26 })}<span class="nav-label">${n.label}</span>${badge}</a>`;
    })
    .join('');

  return `
  <div class="shell">
    <nav class="side" aria-label="メインメニュー">
      <a class="brand hide-sm" href="#/"><span class="brand-mark">${icon('at', { size: 22 })}</span><span class="brand-label">SNS</span></a>
      ${nav}
      <button class="btn btn-primary compose-side hide-sm" type="button" data-action="compose" data-key="compose-side" aria-label="投稿する">${icon('pen')}<span class="label">投稿する</span></button>
    </nav>
    <main id="main" class="main">
      <div class="topbar">
        ${back ? `<button class="icon-btn plain" type="button" data-action="back" aria-label="前の画面に戻る">${icon('back')}</button>` : ''}
        <div>
          <h1 id="page-title" tabindex="-1">${esc(title)}</h1>
          ${sub ? `<div class="sub">${esc(sub)}</div>` : ''}
        </div>
        <span class="spacer"></span>
        ${extra}
      </div>
      ${content}
    </main>
    <aside class="right" aria-label="トレンドと検索">
      <form class="panel" data-form="search" role="search">
        <label class="sr-only" for="side-search">検索</label>
        <input type="search" id="side-search" name="q" placeholder="キーワード・@ユーザーID・#タグで検索">
      </form>
      <section class="panel">
        <h2>トレンド</h2>
        ${trendList(trendTags(5))}
        <a href="#/trends">トレンドをすべて見る</a>
      </section>
      <section class="panel">
        <h2>プロトタイプの操作</h2>
        <p>20秒ごとに、他のユーザーの投稿と通知が自動で届きます。待たずに試すときは、下のボタンを押してください。</p>
        <button class="btn btn-outline btn-block" type="button" data-action="receive" data-key="receive">他のユーザーの投稿を受け取る</button>
      </section>
    </aside>
  </div>
  <button class="btn btn-primary fab" type="button" data-action="compose" data-key="compose-fab">${icon('pen')}投稿する</button>`;
}

function trendList(tags, ranked = false) {
  if (!tags.length) return '<p>直近24時間のトレンドはまだありません</p>';
  return `<ol class="trend-list">${tags
    .map(
      (t, i) => `<li><a href="#/tags/${encodeURIComponent(t.name)}">
        ${ranked ? `<span class="rank">${i + 1}</span>` : ''}
        <span><span class="tag">#${esc(t.name)}</span><br><span class="count">${t.count}件の投稿</span></span></a></li>`,
    )
    .join('')}</ol>`;
}

function viewHome() {
  const tab = state.route.tab;
  const posts = timeline(tab);
  const tabs = `
  <nav class="tabs" aria-label="タイムラインの種類">
    <a class="tab" href="#/" ${tab === 'all' ? 'aria-current="page"' : ''}>すべて</a>
    <a class="tab" href="#/?tab=following" ${tab === 'following' ? 'aria-current="page"' : ''}>フォロー中</a>
  </nav>`;
  const newbar = state.newCount
    ? `<div class="newbar"><button class="btn btn-primary" type="button" data-action="scroll-top">↑ 新しい投稿 ${state.newCount}件</button></div>`
    : '';
  const empty = tab === 'all'
    ? 'まだ投稿がありません。最初の投稿をしてみましょう'
    : 'フォローしている人の投稿がここに表示されます<br><a class="btn btn-primary" href="#/search?tab=users">ユーザーを探す</a>';
  return layout('ホーム', tabs + composer('home') + newbar + postList(posts, empty));
}

function viewDetail() {
  const post = findPost(state.route.id);
  if (!post || state.blocked.includes(post.author)) return viewNotFound();
  const replies = state.posts
    .filter((p) => p.replyTo === post.id && visible(p))
    .sort((a, b) => a.id - b.id);
  let parent = '';
  if (post.replyTo) {
    const pp = findPost(post.replyTo);
    parent = pp && visibleBase(pp) ? postCard(pp) : '<div class="card"><p class="text">この投稿は削除されました</p></div>';
  }
  const main = visibleBase(post)
    ? postCard(post, { big: true, showComposer: true })
    : '<div class="card"><p class="text">この投稿は削除されました</p></div>';
  const list = replies.length
    ? replies.map((p) => postCard(p)).join('')
    : '<div class="empty">まだ返信はありません</div>';
  return layout('投稿', parent + main + '<h2 class="section-title">返信</h2>' + list, { back: true });
}

function viewProfile() {
  const key = userKeyByHandle(state.route.handle);
  if (!key || (state.suspended.includes(key) && key !== ME)) return viewNotFound();
  const u = state.users[key];
  const mine = key === ME;
  const blocked = state.blocked.includes(key);
  const muted = state.muted.includes(key);
  const tab = state.route.tab;
  const band = u.headerUrl ? `url('${esc(u.headerUrl)}') center / cover` : `${u.color}33`;

  const buttons = mine
    ? `<a class="btn btn-outline" href="#/settings">プロフィールを編集</a>`
    : `${blocked ? '' : followButton(key)}
       <div class="menu-wrap">
         <button class="icon-btn plain" type="button" aria-label="その他の操作" aria-expanded="${state.menuFor === 'profile'}" data-action="menu" data-id="profile" data-key="more-profile">${icon('more')}</button>
         ${state.menuFor === 'profile' ? `<div class="menu" role="group" aria-label="ユーザーのメニュー">
           <button type="button" data-action="mute" data-user="${key}" data-key="mute-profile">${muted ? 'ミュートを解除' : 'ミュートする'}</button>
           ${blocked
             ? `<button type="button" data-action="unblock" data-user="${key}" data-key="unblock-profile">ブロックを解除</button>`
             : `<button type="button" class="danger" data-action="block" data-user="${key}" data-key="block-profile">ブロックする</button>`}
         </div>` : ''}
       </div>`;

  const head = `
  <div class="band" style="background: ${band}"></div>
  <section class="profile" aria-label="プロフィール">
    <div class="profile-top">
      <span class="avatar lg" style="background: ${u.avatarUrl ? `${u.color} url('${esc(u.avatarUrl)}') center / cover` : u.color}" aria-hidden="true">${u.avatarUrl ? '' : esc([...u.name][0])}</span>
      <div class="buttons">${buttons}</div>
    </div>
    <h2>${esc(u.name)}</h2>
    <div><span class="handle">@${esc(u.handle)}</span>
      ${state.followers.includes(key) && !mine ? '<span class="chip">フォローされています</span>' : ''}
      ${muted ? '<span class="chip">ミュート中</span>' : ''}</div>
    <p class="bio">${richText(u.bio)}</p>
    <p class="joined">${esc(u.joined)}から利用しています</p>
    <div class="counts">
      <a href="#/users/${esc(u.handle)}/following"><strong>${mine ? state.following.length : u.following}</strong>フォロー</a>
      <a href="#/users/${esc(u.handle)}/followers"><strong>${mine ? state.followers.length : u.followers}</strong>フォロワー</a>
      <span><strong>${state.posts.filter((p) => p.author === key && !p.deleted).length}</strong>投稿</span>
    </div>
  </section>`;

  if (blocked) {
    return layout(u.name, head + `<div class="blocked-box"><p>@${esc(u.handle)} さんをブロックしています</p>
      <button class="btn btn-outline" type="button" data-action="unblock" data-user="${key}" data-key="unblock-box">ブロックを解除</button></div>`, { back: true });
  }

  const base = state.posts.filter((p) => p.author === key && !p.deleted && (visibleBase(p) || (mine && p.hidden)));
  const posts = tab === 'replies' ? base : tab === 'media' ? base.filter((p) => p.images?.length) : base.filter((p) => !p.replyTo);
  const link = (t, label) =>
    `<a class="tab" href="#/users/${esc(u.handle)}?tab=${t}" ${tab === t ? 'aria-current="page"' : ''}>${label}</a>`;
  const tabs = `<nav class="tabs" aria-label="投稿の種類">${link('posts', '投稿')}${link('replies', '返信を含む')}${link('media', '画像')}</nav>`;
  return layout(u.name, head + tabs + postList(posts, 'まだ投稿がありません'), { back: !mine, sub: `${posts.length}件の投稿` });
}

function viewFollows() {
  const key = userKeyByHandle(state.route.handle);
  if (!key) return viewNotFound();
  const u = state.users[key];
  const others = Object.keys(state.users).filter((k) => k !== key && !state.blocked.includes(k) && !state.suspended.includes(k));
  const isFollowing = state.route.tab === 'following';
  const list = key === ME
    ? (isFollowing ? state.following : state.followers).filter((k) => others.includes(k))
    : others.slice(0, isFollowing ? 3 : 4);
  const link = (t, label) =>
    `<a class="tab" href="#/users/${esc(u.handle)}/${t}" ${state.route.tab === t ? 'aria-current="page"' : ''}>${label}</a>`;
  const body = list.length
    ? list.map((k) => userRow(k)).join('')
    : `<div class="empty">${isFollowing ? 'まだフォローしていません' : 'まだフォロワーはいません'}</div>`;
  return layout(u.name, `<nav class="tabs" aria-label="一覧の種類">${link('following', 'フォロー中')}${link('followers', 'フォロワー')}</nav>` + body, { back: true, sub: `@${u.handle}` });
}

const NOTIFICATION_TEXT = {
  LIKE: { icon: 'heart', text: 'さんがあなたの投稿にいいねしました' },
  REPLY: { icon: 'reply', text: 'さんがあなたの投稿に返信しました' },
  FOLLOW: { icon: 'user_plus', text: 'さんがあなたをフォローしました' },
  MENTION: { icon: 'at', text: 'さんがあなたをメンションしました' },
};

function viewNotifications() {
  const items = state.notifications.filter(notificationVisible);
  const body = items.length
    ? items
        .map((n) => {
          const actor = state.users[n.actor];
          const t = NOTIFICATION_TEXT[n.type];
          const post = n.post ? findPost(n.post) : null;
          const href = n.type === 'FOLLOW' ? `#/users/${actor.handle}` : `#/posts/${n.post}`;
          return `
          <a class="notif${n.fresh ? ' unread' : ''}" href="${href}">
            <span class="kind${n.type === 'LIKE' ? ' like' : ''}">${icon(t.icon, { size: 28, filled: n.type === 'LIKE' })}</span>
            <span class="body">
              <span>${n.fresh ? '<span class="unread-label">未読</span>' : ''}<strong>${esc(actor.name)}</strong>${t.text}</span>
              ${post ? `<span class="preview" style="display:block">${esc([...post.text].slice(0, 40).join(''))}${[...post.text].length > 40 ? '…' : ''}</span>` : ''}
              <span class="when">${relTime(n.at)}</span>
            </span>
          </a>`;
        })
        .join('')
    : '<div class="empty">まだ通知はありません</div>';
  return layout('通知', body);
}

function viewSearch() {
  const { q, tab } = state.route;
  const tabLink = (t, label) =>
    `<a class="tab" href="#/search?q=${encodeURIComponent(q)}&tab=${t}" ${tab === t ? 'aria-current="page"' : ''}>${label}</a>`;
  const form = `
  <form class="search-form" data-form="search" role="search">
    <label class="sr-only" for="search-q">検索するキーワード</label>
    <input type="search" id="search-q" name="q" value="${esc(q)}" maxlength="200" placeholder="キーワード・@ユーザーID・#タグで検索">
    <button class="btn btn-primary" type="submit">検索</button>
  </form>`;
  const tabs = `<nav class="tabs" aria-label="検索の種類">${tabLink('posts', '投稿')}${tabLink('users', 'ユーザー')}${tabLink('tags', 'ハッシュタグ')}</nav>`;

  if (!q.trim()) {
    const hint = tab === 'users'
      ? Object.keys(state.users).filter((k) => k !== ME && !state.blocked.includes(k) && !state.suspended.includes(k)).map((k) => userRow(k)).join('')
      : `<h2 class="section-title">いまのトレンド</h2>${trendList(trendTags(5), true)}<p style="padding: 0 16px"><a href="#/trends">トレンドをすべて見る</a></p>`;
    return layout('検索', form + tabs + hint);
  }
  if ([...q.trim()].length > 100) {
    return layout('検索', form + '<p class="field-error" style="padding: 0 16px">100文字以内で入力してください</p>');
  }

  const empty = `『${esc(q)}』に一致する結果はありません`;
  let results;
  if (tab === 'users') {
    const word = q.replace(/^@/, '');
    const keys = Object.keys(state.users)
      .filter((k) => !state.blocked.includes(k) && !state.suspended.includes(k))
      .filter((k) => matchesAll(`${state.users[k].handle} ${state.users[k].name}`, word))
      .sort((a, b) => Number(normalize(state.users[b].handle).startsWith(normalize(word))) - Number(normalize(state.users[a].handle).startsWith(normalize(word))) || state.users[b].followers - state.users[a].followers);
    results = keys.length ? keys.map((k) => userRow(k)).join('') : `<div class="empty">${empty}</div>`;
  } else if (tab === 'tags') {
    const word = normalize(q.trim().replace(/^#/, ''));
    const counts = new Map();
    for (const p of state.posts) {
      if (!visibleBase(p)) continue;
      for (const t of extractTags(p.text)) if (t.includes(word)) counts.set(t, (counts.get(t) ?? 0) + 1);
    }
    const tags = [...counts].map(([name, count]) => ({ name, count }))
      .sort((a, b) => Number(b.name.startsWith(word)) - Number(a.name.startsWith(word)) || b.count - a.count);
    results = tags.length ? trendList(tags) : `<div class="empty">${empty}</div>`;
  } else {
    results = postList(state.posts.filter((p) => visible(p) && matchesAll(p.text, q)), empty);
  }
  return layout('検索', form + tabs + results);
}

function viewTrends() {
  const tags = trendTags(10);
  const extra = `<button class="btn btn-outline" type="button" data-action="refresh" data-key="refresh">${icon('refresh', { size: 20 })}更新</button>`;
  const body = `
    <h2 class="section-title">ハッシュタグ</h2>
    ${trendList(tags, true)}
    <h2 class="section-title">人気の投稿</h2>
    ${postList(popularPosts(), '直近24時間のトレンドはまだありません')}`;
  return layout('トレンド', body, { sub: '直近24時間', extra });
}

function viewTag() {
  const tag = normalize(state.route.tag);
  const posts = state.posts.filter((p) => visible(p) && extractTags(p.text).includes(tag));
  return layout(`#${tag}`, postList(posts, `#${esc(tag)} を含む投稿はありません`), { back: true, sub: `${posts.length}件の投稿` });
}

function viewBookmarks() {
  // 解除しても、画面を開き直すまでは一覧から消さない(押し間違えたときに戻せるように)
  const posts = state.bookmarksShown.map(findPost).filter((p) => p && visible(p));
  return layout('ブックマーク', postList(posts, 'ブックマークした投稿はここに表示されます。投稿のしおりのボタンで保存できます'), { sub: 'あなただけに表示されます' });
}

function viewSettings() {
  const u = me();
  const themes = [['system', '端末に合わせる'], ['light', 'ライト'], ['dark', 'ダーク']];
  const listOf = (keys, action, label, emptyText) =>
    keys.length
      ? keys.map((k) => userRow(k, {
          action: `<button class="btn btn-outline" type="button" data-action="${action}" data-user="${k}" data-key="${action}-${k}">${label}</button>`,
        })).join('')
      : `<p>${emptyText}</p>`;
  const body = `
  <section class="settings-section">
    <h2>プロフィール</h2>
    <form data-form="profile" novalidate>
      <div class="field">
        <span class="label">アイコン画像</span>
        <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
          <span class="avatar lg" style="background: ${u.avatarUrl ? `${u.color} url('${esc(u.avatarUrl)}') center / cover` : u.color}; border: 0" aria-hidden="true">${u.avatarUrl ? '' : esc([...u.name][0])}</span>
          <input class="sr-only" type="file" id="avatar-file" tabindex="-1" accept="${IMAGE_TYPES.join(',')}" data-change="avatar">
          <button class="btn btn-outline" type="button" data-action="pick-avatar" data-key="pick-avatar">画像を選ぶ</button>
          ${u.avatarUrl ? '<button class="btn btn-outline" type="button" data-action="remove-avatar" data-key="remove-avatar">削除</button>' : ''}
        </div>
      </div>
      <div class="field">
        <label for="profile-name">表示名(必須)</label>
        <input type="text" id="profile-name" name="name" value="${esc(u.name)}" aria-describedby="profile-name-error">
        <p class="field-error" id="profile-name-error" hidden></p>
      </div>
      <div class="field">
        <label for="profile-bio">自己紹介</label>
        <textarea id="profile-bio" name="bio" aria-describedby="profile-bio-hint profile-bio-error">${esc(u.bio)}</textarea>
        <p class="hint" id="profile-bio-hint">160文字まで</p>
        <p class="field-error" id="profile-bio-error" hidden></p>
      </div>
      <button class="btn btn-primary" type="submit">保存する</button>
    </form>
  </section>
  <section class="settings-section">
    <h2>表示</h2>
    <fieldset style="border: 0; padding: 0; margin: 0">
      <legend class="label" style="font-weight: 700; margin-bottom: 8px">表示モード</legend>
      <div class="radio-row">
        ${themes.map(([value, label]) => `<label><input type="radio" name="theme" value="${value}" data-change="theme" ${state.theme === value ? 'checked' : ''}>${label}</label>`).join('')}
      </div>
    </fieldset>
  </section>
  <section class="settings-section">
    <h2>ミュート中のユーザー</h2>
    ${listOf(state.muted, 'mute', 'ミュートを解除', 'ミュートしているユーザーはいません')}
  </section>
  <section class="settings-section">
    <h2>ブロック中のユーザー</h2>
    ${listOf(state.blocked, 'unblock', 'ブロックを解除', 'ブロックしているユーザーはいません')}
  </section>
  <section class="settings-section">
    <h2>アカウント</h2>
    <p>メールアドレス: ${esc(u.email)}</p>
    <button class="btn btn-outline" type="button" data-action="logout" data-key="logout">ログアウト</button>
  </section>`;
  return layout('設定', body);
}

function viewAdmin() {
  if (!isAdmin()) return viewNotFound();
  const { tab, q } = state.route;
  const tabLink = (t, label) => `<a class="tab" href="#/admin?tab=${t}" ${tab === t ? 'aria-current="page"' : ''}>${label}</a>`;
  const form = `
  <form class="search-form" data-form="admin-search" role="search">
    <label class="sr-only" for="admin-q">${tab === 'users' ? 'ユーザーID・表示名・メールアドレスで検索' : '本文で検索'}</label>
    <input type="search" id="admin-q" name="q" value="${esc(q)}" placeholder="${tab === 'users' ? 'ユーザーID・表示名・メールアドレスで検索' : '本文で検索'}">
    <button class="btn btn-primary" type="submit">検索</button>
  </form>`;

  let rows;
  if (tab === 'users') {
    const keys = Object.keys(state.users).filter((k) => {
      const u = state.users[k];
      return !q || matchesAll(`${u.handle} ${u.name} ${u.email}`, q);
    });
    rows = `<div class="admin-row users head" aria-hidden="true"><span>ID</span><span>ユーザー</span><span>メールアドレス</span><span>状態</span><span>操作</span></div>` +
      keys.map((k, i) => {
        const u = state.users[k];
        const suspended = state.suspended.includes(k);
        const canAct = k !== ME && u.role !== 'ADMIN';
        return `<div class="admin-row users">
          <span>${i + 1}</span>
          <span><a href="#/users/${esc(u.handle)}">@${esc(u.handle)}</a><br>${esc(u.name)}${u.role === 'ADMIN' ? '<br><span class="chip">管理者</span>' : ''}</span>
          <span class="wide">${esc(u.email)}</span>
          <span><span class="status${suspended ? ' bad' : ''}">${suspended ? '利用停止中' : '利用中'}</span></span>
          <span>${canAct ? `<button class="btn ${suspended ? 'btn-outline' : 'btn-danger'}" type="button" data-action="suspend" data-user="${k}" data-key="suspend-${k}">${suspended ? '停止を解除' : '利用停止にする'}</button>` : ''}</span>
        </div>`;
      }).join('');
  } else {
    const posts = state.posts.filter((p) => !p.deleted && (!q || matchesAll(p.text, q)));
    rows = `<div class="admin-row head" aria-hidden="true"><span>ID</span><span>投稿者</span><span>本文</span><span>状態</span><span>操作</span></div>` +
      posts.map((p) => {
        const author = state.users[p.author];
        const status = p.hidden ? '非表示' : state.suspended.includes(p.author) ? '投稿者が利用停止中' : '公開中';
        return `<div class="admin-row">
          <span>${p.id}</span>
          <span><a href="#/users/${esc(author.handle)}">@${esc(author.handle)}</a></span>
          <span class="wide"><a href="#/posts/${p.id}">${esc([...p.text].slice(0, 80).join('')) || '(画像のみ)'}</a><br><span class="handle" style="font-size: 14px">${fullTime(p.at)}</span></span>
          <span><span class="status${status === '公開中' ? '' : ' bad'}">${status}</span></span>
          <span><button class="btn ${p.hidden ? 'btn-outline' : 'btn-danger'}" type="button" data-action="hide" data-id="${p.id}" data-key="hide-${p.id}">${p.hidden ? '表示に戻す' : '非表示にする'}</button></span>
        </div>`;
      }).join('');
  }
  const body = `<nav class="tabs" aria-label="管理する対象">${tabLink('posts', '投稿')}${tabLink('users', 'ユーザー')}</nav>${form}<div class="admin-table">${rows}</div>`;
  return layout('管理者画面', body, { sub: 'プロトタイプでは、たろうが管理者です' });
}

function viewNotFound() {
  return layout('ページが見つかりません', `
    <div class="empty">
      <p>お探しのページは削除されたか、URL が間違っている可能性があります。</p>
      <a class="btn btn-primary" href="#/">ホームへ戻る</a>
    </div>`);
}

function authPage(title, inner) {
  return `
  <main id="main" class="auth">
    <div class="auth-card">
      <div class="brand"><span class="brand-mark">${icon('at', { size: 22 })}</span>SNS</div>
      <h1 id="page-title" tabindex="-1">${esc(title)}</h1>
      ${inner}
    </div>
  </main>`;
}

function viewLogin() {
  return authPage('ログイン', `
    <button class="btn btn-outline btn-block" type="button" data-action="google-login">Google でログイン</button>
    <div class="divider">または</div>
    <form data-form="login" novalidate>
      <p class="field-error" id="login-error" hidden></p>
      <div class="field">
        <label for="login-email">メールアドレス(必須)</label>
        <input type="email" id="login-email" name="email" autocomplete="email" value="taro@example.com">
      </div>
      <div class="field">
        <label for="login-password">パスワード(必須)</label>
        <div class="pw-row">
          <input type="password" id="login-password" name="password" autocomplete="current-password" value="password123">
          <button class="btn btn-outline" type="button" data-action="toggle-password" data-target="login-password" aria-pressed="false">表示</button>
        </div>
        <p class="hint">プロトタイプ: 入力済みのまま「ログイン」を押してください(8文字以上なら何でも通ります)</p>
      </div>
      <button class="btn btn-primary btn-block" type="submit">ログイン</button>
    </form>
    <div class="auth-links">
      <a href="#/login" data-action="not-in-prototype">パスワードを忘れた方</a>
      <a href="#/signup">アカウントを作成する</a>
    </div>`);
}

function viewSignup() {
  if (state.signupSent) {
    return authPage('確認メールを送りました', `
      <p>メールのリンクを開いて、登録を完了してください(リンクの有効期限は24時間です)。</p>
      <p class="hint">プロトタイプでは、実際にはメールを送りません。下のボタンで「メールのリンクを開いた」ことにして、次の画面へ進めます。</p>
      <a class="btn btn-primary btn-block" href="#/onboarding">メールのリンクを開いたことにする</a>
      <div class="auth-links"><a href="#/login">ログインへ</a></div>`);
  }
  return authPage('アカウントを作成', `
    <button class="btn btn-outline btn-block" type="button" data-action="google-signup">Google で登録</button>
    <div class="divider">または</div>
    <form data-form="signup" novalidate>
      <div class="field">
        <label for="signup-email">メールアドレス(必須)</label>
        <input type="email" id="signup-email" name="email" autocomplete="email" aria-describedby="signup-email-error">
        <p class="field-error" id="signup-email-error" hidden></p>
      </div>
      <div class="field">
        <label for="signup-password">パスワード(必須)</label>
        <div class="pw-row">
          <input type="password" id="signup-password" name="password" autocomplete="new-password" aria-describedby="signup-password-hint signup-password-error">
          <button class="btn btn-outline" type="button" data-action="toggle-password" data-target="signup-password" aria-pressed="false">表示</button>
        </div>
        <p class="hint" id="signup-password-hint">8文字以上72文字以内</p>
        <p class="field-error" id="signup-password-error" hidden></p>
      </div>
      <button class="btn btn-primary btn-block" type="submit">登録する</button>
    </form>
    <div class="auth-links"><a href="#/login">アカウントをお持ちの方はログイン</a></div>`);
}

function viewOnboarding() {
  return authPage('プロフィールを設定しましょう', `
    <form data-form="onboarding" novalidate>
      <div class="field">
        <label for="onb-handle">ユーザーID(必須)</label>
        <div class="handle-input"><span aria-hidden="true">@</span>
          <input type="text" id="onb-handle" name="handle" data-input="handle" autocomplete="off" aria-describedby="onb-handle-hint onb-handle-status">
        </div>
        <p class="hint" id="onb-handle-hint">半角英数字と_の3〜15文字。あとから変更できません</p>
        <p id="onb-handle-status" aria-live="polite"></p>
      </div>
      <div class="field">
        <label for="onb-name">表示名(必須)</label>
        <input type="text" id="onb-name" name="name" aria-describedby="onb-name-error">
        <p class="field-error" id="onb-name-error" hidden></p>
      </div>
      <button class="btn btn-primary btn-block" type="submit">はじめる</button>
    </form>`);
}

// ---- 画面の切り替え ----
function parseRoute() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, query = ''] = raw.split('?');
  const params = new URLSearchParams(query);
  const parts = path.split('/').filter(Boolean).map(decodeURIComponent);
  if (!parts.length) return { name: 'home', tab: params.get('tab') === 'following' ? 'following' : 'all' };
  switch (parts[0]) {
    case 'login': return { name: 'login' };
    case 'signup': return { name: 'signup' };
    case 'onboarding': return { name: 'onboarding' };
    case 'posts': return { name: 'detail', id: Number(parts[1]), reply: params.has('reply') };
    case 'users':
      if (parts[2] === 'following' || parts[2] === 'followers') return { name: 'follows', handle: parts[1], tab: parts[2] };
      return { name: 'profile', handle: parts[1], tab: ['replies', 'media'].includes(params.get('tab')) ? params.get('tab') : 'posts' };
    case 'notifications': return { name: 'notifications' };
    case 'search': {
      const q = params.get('q') ?? '';
      const tab = params.get('tab') ?? (q.startsWith('@') ? 'users' : q.startsWith('#') ? 'tags' : 'posts');
      return { name: 'search', q, tab: ['posts', 'users', 'tags'].includes(tab) ? tab : 'posts' };
    }
    case 'trends': return { name: 'trends' };
    case 'tags': return { name: 'tag', tag: parts[1] ?? '' };
    case 'bookmarks': return { name: 'bookmarks' };
    case 'settings': return { name: 'settings' };
    case 'admin': return { name: 'admin', tab: params.get('tab') === 'users' ? 'users' : 'posts', q: params.get('q') ?? '' };
    default: return { name: 'notfound' };
  }
}

const VIEWS = {
  login: viewLogin, signup: viewSignup, onboarding: viewOnboarding,
  home: viewHome, detail: viewDetail, profile: viewProfile, follows: viewFollows,
  notifications: viewNotifications, search: viewSearch, trends: viewTrends, tag: viewTag,
  bookmarks: viewBookmarks, settings: viewSettings, admin: viewAdmin, notfound: viewNotFound,
};
const PUBLIC_ROUTES = ['login', 'signup', 'onboarding'];

function onRouteChange() {
  const prev = state.route;
  const next = parseRoute();

  if (!state.loggedIn && !PUBLIC_ROUTES.includes(next.name)) {
    state.afterLogin = location.hash || '#/';
    location.replace('#/login');
    return;
  }
  if (state.loggedIn && (next.name === 'login' || next.name === 'signup')) {
    location.replace('#/');
    return;
  }

  // 通知の画面を出たら「未読」の印を外す。開いたら既読にする(印は開いている間だけ残す)
  if (prev.name === 'notifications') state.notifications.forEach((n) => { n.fresh = false; });
  if (next.name === 'notifications') {
    state.notifications.forEach((n) => { n.fresh = !n.read; n.read = true; });
  }
  if (next.name === 'bookmarks') {
    state.bookmarksShown = state.posts.filter((p) => p.bookmarked).map((p) => p.id);
  }
  if (prev.name === 'home' || next.name !== 'home') state.posts.forEach((p) => { p.isNew = false; });

  state.route = next;
  state.menuFor = null;
  state.newCount = 0;
  render();
  window.scrollTo(0, 0);

  const titleEl = document.getElementById('page-title');
  document.title = `${titleEl ? titleEl.textContent : 'SNS'} / SNS`;
  if (next.name === 'detail' && next.reply) {
    document.getElementById('text-reply')?.focus();
  } else {
    titleEl?.focus();
  }
}

// 画面を書き直す。キーボードで選んでいた場所(data-key か id)は、書き直したあとも選んだままにする
function render() {
  state.pendingRender = false;
  const active = document.activeElement;
  const key = active && app.contains(active) ? (active.dataset.key ? `[data-key="${active.dataset.key}"]` : active.id ? `#${CSS.escape(active.id)}` : '') : '';
  app.innerHTML = (VIEWS[state.route.name] ?? viewNotFound)();
  for (const kind of Object.keys(state.drafts)) updateComposer(kind);
  if (key) app.querySelector(key)?.focus({ preventScroll: true });
}

// 入力中に画面を書き直すと入力の邪魔になるので、入力欄から離れるまで待つ
function renderSoon() {
  const active = document.activeElement;
  const typing = active && app.contains(active) && (active.tagName === 'TEXTAREA' || active.tagName === 'INPUT');
  if (typing) state.pendingRender = true;
  else render();
}

// ---- お知らせ欄 ----
let toastTimer;
function toast(message, { error = false } = {}) {
  const el = $('#toast');
  clearTimeout(toastTimer);
  el.className = 'toast' + (error ? ' error' : '');
  el.innerHTML = error
    ? `<span>エラー: ${esc(message)}</span><button type="button" data-action="close-toast">閉じる</button>`
    : `<span>${esc(message)}</span>`;
  el.hidden = false;
  if (!error) toastTimer = setTimeout(() => { el.hidden = true; }, 5000);
}
const announce = (message) => { $('#live').textContent = message; };

// ---- ダイアログ(閉じたら、開いたときのボタンにキーボードの位置を戻す) ----
const dialog = $('#dialog');
let dialogOpener = '';
let dialogConfirm = null;
let viewerState = null;

function openDialog(html, { className = '', focus = '[data-default]' } = {}) {
  const active = document.activeElement;
  dialogOpener = active?.dataset?.key ? `[data-key="${active.dataset.key}"]` : '';
  dialog.className = className;
  dialog.innerHTML = html;
  dialog.showModal();
  dialog.querySelector(focus)?.focus();
}

function closeDialog() {
  if (dialog.open) dialog.close();
}

dialog.addEventListener('close', () => {
  dialogConfirm = null;
  viewerState = null;
  dialog.innerHTML = '';
  const target = (dialogOpener && document.querySelector(dialogOpener)) || document.getElementById('page-title');
  target?.focus();
});

// 入力途中で閉じようとしたら確かめる(画面仕様定義書 3.5)
dialog.addEventListener('cancel', (e) => {
  const draft = state.drafts.dialog;
  if (dialog.querySelector('[data-form="post"]') && (draft.text.trim() || draft.images.length)) {
    if (!confirm('入力した内容は消えます。閉じますか?')) e.preventDefault();
    else state.drafts.dialog = emptyDraft();
  }
});

function confirmDialog({ title, body, okLabel, onOk, danger = true }) {
  dialogConfirm = onOk;
  openDialog(`
    <h2 id="dialog-title">${esc(title)}</h2>
    ${body}
    <div class="dialog-buttons">
      <button class="btn btn-outline" type="button" data-action="close-dialog" data-default>やめる</button>
      <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="button" data-action="confirm-dialog">${esc(okLabel)}</button>
    </div>`);
}

function renderViewer() {
  const { post, index } = viewerState;
  const images = post.images;
  const im = images[index];
  dialog.innerHTML = `
    <h2 id="dialog-title" class="sr-only">画像の拡大表示</h2>
    <div class="viewer-img">${im.url ? `<img src="${esc(im.url)}" alt="${esc(im.alt)}">` : `<span class="img"><span class="ph" style="background: ${im.tone}" role="img" aria-label="${esc(im.alt)}">${esc(im.alt)}</span></span>`}</div>
    <div class="viewer-bar">
      <button class="btn btn-outline" type="button" data-action="viewer-prev" ${index === 0 ? 'disabled' : ''} aria-label="前の画像">←</button>
      <span>${index + 1} / ${images.length}</span>
      <button class="btn btn-outline" type="button" data-action="viewer-next" ${index === images.length - 1 ? 'disabled' : ''} aria-label="次の画像">→</button>
      <span class="alt">${esc(im.alt || '(説明なし)')}</span>
      <button class="btn btn-primary" type="button" data-action="close-dialog" data-default>閉じる</button>
    </div>`;
}

dialog.addEventListener('keydown', (e) => {
  if (!viewerState) return;
  if (e.key === 'ArrowLeft' && viewerState.index > 0) { viewerState.index -= 1; renderViewer(); dialog.querySelector('[data-default]').focus(); }
  if (e.key === 'ArrowRight' && viewerState.index < viewerState.post.images.length - 1) { viewerState.index += 1; renderViewer(); dialog.querySelector('[data-default]').focus(); }
});

// 画像の外(背景)をクリックしたら閉じる
dialog.addEventListener('click', (e) => {
  if (e.target === dialog && viewerState) closeDialog();
});

// ---- 操作 ----
function toggleFollow(key) {
  const u = state.users[key];
  if (state.following.includes(key)) {
    state.following = state.following.filter((k) => k !== key);
    u.followers -= 1;
    toast(`@${u.handle} さんのフォローを解除しました`);
  } else {
    state.following.push(key);
    u.followers += 1;
    toast(`@${u.handle} さんをフォローしました`);
  }
}

function block(key) {
  const u = state.users[key];
  state.blocked.push(key);
  if (state.following.includes(key)) { state.following = state.following.filter((k) => k !== key); u.followers -= 1; }
  state.followers = state.followers.filter((k) => k !== key);
  toast(`@${u.handle} さんをブロックしました`);
}

function addNotification(type, actor, post) {
  if (actor === ME || state.blocked.includes(actor) || state.muted.includes(actor)) return;
  const onPage = state.route.name === 'notifications';
  const same = (type === 'LIKE' || type === 'FOLLOW') &&
    state.notifications.find((n) => n.type === type && n.actor === actor && n.post === post);
  if (same) {
    state.notifications = state.notifications.filter((n) => n !== same);
    Object.assign(same, { at: Date.now(), read: onPage, fresh: onPage });
    state.notifications.unshift(same);
  } else {
    state.notifications.unshift({ id: state.nextNotificationId++, type, actor, post, at: Date.now(), read: onPage, fresh: onPage });
  }
  if (!onPage) announce(`通知が届きました。未読は ${unreadCount()}件です`);
}

// 他のユーザーの投稿を受け取る(本番ではサーバーから SSE で届く)
function receive() {
  if (!state.loggedIn) return;
  const item = INCOMING[state.incomingIndex % INCOMING.length];
  state.incomingIndex += 1;
  const post = { id: state.nextPostId++, author: item.author, text: item.text, at: Date.now(), likes: 0, images: item.images ?? [], isNew: true };
  state.posts.unshift(post);
  if (item.text.includes(`@${me().handle}`)) addNotification('MENTION', item.author, post.id);

  // 2 回に 1 回、自分の投稿にいいねが付く(通知のバッジが増える様子を見せる)
  if (state.incomingIndex % 2 === 0) {
    const mine = state.posts.find((p) => p.author === ME && !p.replyTo && visibleBase(p));
    const liker = LIKERS[(state.incomingIndex / 2) % LIKERS.length];
    if (mine) { mine.likes += 1; addNotification('LIKE', liker, mine.id); }
  }

  const r = state.route;
  const showsOnHome = r.name === 'home' && visible(post) && (r.tab === 'all' || state.following.includes(post.author));
  const scrolled = window.scrollY > 200;
  if (showsOnHome && scrolled) state.newCount += 1;

  // 下を読んでいる間に先頭へ足したときは、その分だけスクロールして、見ている投稿が動かないようにする
  const before = document.documentElement.scrollHeight;
  renderSoon();
  if (showsOnHome && scrolled && !state.pendingRender) window.scrollBy(0, document.documentElement.scrollHeight - before);
}

function submitPost(form) {
  const kind = form.dataset.kind;
  const draft = state.drafts[kind];
  const count = charCount(draft.text);
  if (count === 0 && !draft.images.length) draft.error = '本文か画像のどちらかを入力してください';
  else if (count > MAX_TEXT) draft.error = '1000文字以内で入力してください';
  else draft.error = '';
  if (draft.error) {
    render();
    document.getElementById(`text-${kind}`)?.focus();
    return;
  }
  const replyTo = form.dataset.replyTo ? Number(form.dataset.replyTo) : undefined;
  const post = { id: state.nextPostId++, author: ME, text: draft.text.trim(), at: Date.now(), likes: 0, images: draft.images, replyTo };
  state.posts.unshift(post);
  state.drafts[kind] = emptyDraft();
  if (kind === 'dialog') closeDialog();
  render();
  toast(replyTo ? '返信しました' : '投稿しました');
}

function addImages(kind, files) {
  const draft = state.drafts[kind];
  const errors = [];
  for (const file of files) {
    if (!IMAGE_TYPES.includes(file.type)) { errors.push('JPEG・PNG・WebP・GIF の画像を選んでください'); continue; }
    if (file.size > MAX_IMAGE_BYTES) { errors.push('5MB以下の画像を選んでください'); continue; }
    if (draft.images.length >= MAX_IMAGES) { errors.push('画像は10枚までです'); break; }
    draft.images.push({ url: URL.createObjectURL(file), alt: '' });
  }
  draft.error = [...new Set(errors)].join(' / ');
  if (kind === 'dialog') rerenderComposeDialog();
  else render();
  document.querySelector(`[data-key="pick-${kind}"]`)?.focus();
}

function openComposeDialog() {
  openDialog(`<h2 id="dialog-title">投稿する</h2><div id="dialog-composer">${composer('dialog')}</div>
    <div class="dialog-buttons"><button class="btn btn-outline" type="button" data-action="cancel-compose">やめる</button></div>`, { focus: '#text-dialog' });
  updateComposer('dialog');
}

function rerenderComposeDialog() {
  const holder = document.getElementById('dialog-composer');
  if (!holder) return;
  holder.innerHTML = composer('dialog');
  updateComposer('dialog');
}

function showFieldError(id, message) {
  const el = document.getElementById(id);
  if (!el) return false;
  el.textContent = message;
  el.hidden = !message;
  return Boolean(message);
}

function login(destination) {
  state.loggedIn = true;
  const next = destination ?? state.afterLogin ?? '#/';
  state.afterLogin = '#/';
  location.hash = next;
  if (location.hash === next) onRouteChange();
}

const ACTIONS = {
  back: () => history.back(),
  'scroll-top': () => {
    state.newCount = 0;
    window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    render();
    document.getElementById('page-title')?.focus({ preventScroll: true });
  },
  like: ({ id }) => {
    const p = findPost(Number(id));
    p.liked = !p.liked;
    p.likes += p.liked ? 1 : -1;
    render();
  },
  bookmark: ({ id }) => {
    const p = findPost(Number(id));
    p.bookmarked = !p.bookmarked;
    render();
    toast(p.bookmarked ? 'ブックマークしました' : 'ブックマークを解除しました');
  },
  menu: ({ id }) => {
    const value = id === 'profile' ? 'profile' : Number(id);
    state.menuFor = state.menuFor === value ? null : value;
    render();
    document.querySelector('.menu button')?.focus();
  },
  edit: ({ id }) => {
    const p = findPost(Number(id));
    state.menuFor = null;
    render();
    dialogOpener = `[data-key="more-${p.id}"]`;
    openDialog(`
      <h2 id="dialog-title">投稿を編集</h2>
      <form data-form="edit" data-id="${p.id}" novalidate>
        <label class="sr-only" for="edit-text">投稿の本文</label>
        <textarea id="edit-text" name="text" aria-describedby="edit-error">${esc(p.text)}</textarea>
        <p class="field-error" id="edit-error" hidden></p>
        ${p.images?.length ? '<p class="hint">画像は変更できません</p>' : ''}
        <div class="dialog-buttons">
          <button class="btn btn-outline" type="button" data-action="close-dialog">やめる</button>
          <button class="btn btn-primary" type="submit">保存する</button>
        </div>
      </form>`, { focus: '#edit-text' });
    dialogOpener = `[data-key="more-${p.id}"]`;
  },
  delete: ({ id }, el) => {
    const p = findPost(Number(id));
    state.menuFor = null;
    render();
    confirmDialog({
      title: 'この投稿を削除しますか?',
      body: '<p>削除すると元に戻せません。</p>',
      okLabel: '削除する',
      onOk: () => {
        p.deleted = true;
        p.text = '';
        p.images = [];
        closeDialog();
        if (state.route.name === 'detail' && state.route.id === p.id) history.back();
        else render();
        toast('投稿を削除しました');
      },
    });
    dialogOpener = `[data-key="${el.dataset.key.replace(/^delete-/, 'more-')}"]`;
  },
  mute: ({ user }) => {
    const u = state.users[user];
    state.menuFor = null;
    if (state.muted.includes(user)) {
      state.muted = state.muted.filter((k) => k !== user);
      toast(`@${u.handle} さんのミュートを解除しました`);
    } else {
      state.muted.push(user);
      toast(`@${u.handle} さんをミュートしました。このユーザーの投稿はタイムラインに表示されません`);
    }
    render();
  },
  block: ({ user }, el) => {
    const u = state.users[user];
    state.menuFor = null;
    render();
    confirmDialog({
      title: `@${u.handle} さんをブロックしますか?`,
      body: `<ul><li>お互いの投稿が見えなくなります</li><li>お互いのフォローが解除されます</li><li>相手はあなたをフォロー・返信・いいねできなくなります</li></ul>`,
      okLabel: 'ブロックする',
      onOk: () => { block(user); closeDialog(); render(); },
    });
    dialogOpener = `[data-key="${el.dataset.key.replace(/^block-/, 'more-')}"]`;
  },
  unblock: ({ user }) => {
    state.blocked = state.blocked.filter((k) => k !== user);
    state.menuFor = null;
    toast(`@${state.users[user].handle} さんのブロックを解除しました`);
    render();
  },
  follow: ({ user }) => { toggleFollow(user); render(); },
  compose: () => openComposeDialog(),
  'cancel-compose': () => {
    const draft = state.drafts.dialog;
    if ((draft.text.trim() || draft.images.length) && !confirm('入力した内容は消えます。閉じますか?')) return;
    state.drafts.dialog = emptyDraft();
    closeDialog();
  },
  'pick-images': ({ kind }) => document.getElementById(`file-${kind}`)?.click(),
  'remove-image': ({ kind, index }) => {
    state.drafts[kind].images.splice(Number(index), 1);
    if (kind === 'dialog') rerenderComposeDialog(); else render();
    document.querySelector(`[data-key="pick-${kind}"]`)?.focus();
  },
  'view-image': ({ id, index }) => {
    const post = findPost(Number(id));
    openDialog('', { className: 'viewer' });
    viewerState = { post, index: Number(index) };
    renderViewer();
    dialog.querySelector('[data-default]')?.focus();
  },
  'viewer-prev': () => { viewerState.index -= 1; renderViewer(); dialog.querySelector('[data-default]').focus(); },
  'viewer-next': () => { viewerState.index += 1; renderViewer(); dialog.querySelector('[data-default]').focus(); },
  'close-dialog': () => closeDialog(),
  'confirm-dialog': () => dialogConfirm?.(),
  'close-toast': () => { $('#toast').hidden = true; },
  receive: () => receive(),
  refresh: () => { render(); toast('トレンドを更新しました'); },
  suspend: ({ user }) => {
    const u = state.users[user];
    if (state.suspended.includes(user)) {
      state.suspended = state.suspended.filter((k) => k !== user);
      render();
      toast(`@${u.handle} さんの利用停止を解除しました`);
      return;
    }
    confirmDialog({
      title: `@${u.handle} さんを利用停止にしますか?`,
      body: '<p>すぐにログアウトされ、投稿も表示されなくなります。</p>',
      okLabel: '実行する',
      onOk: () => { state.suspended.push(user); closeDialog(); render(); toast(`@${u.handle} さんを利用停止にしました`); },
    });
  },
  hide: ({ id }) => {
    const p = findPost(Number(id));
    if (p.hidden) {
      p.hidden = false;
      render();
      toast('投稿を表示に戻しました');
      return;
    }
    confirmDialog({
      title: 'この投稿を非表示にしますか?',
      body: '<p>投稿者以外には表示されなくなります。</p>',
      okLabel: '実行する',
      onOk: () => { p.hidden = true; closeDialog(); render(); toast('投稿を非表示にしました'); },
    });
  },
  'pick-avatar': () => document.getElementById('avatar-file')?.click(),
  'remove-avatar': () => { me().avatarUrl = null; render(); toast('アイコン画像を削除しました'); },
  'toggle-password': ({ target }, el) => {
    const input = document.getElementById(target);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    el.textContent = show ? '隠す' : '表示';
    el.setAttribute('aria-pressed', String(show));
  },
  'google-login': () => login(),
  'google-signup': () => { location.hash = '#/onboarding'; },
  'not-in-prototype': () => toast('プロトタイプでは、この画面は省略しています'),
  logout: () => {
    state.loggedIn = false;
    location.hash = '#/login';
    toast('ログアウトしました');
  },
};

const FORMS = {
  post: (form) => submitPost(form),
  search: (form) => {
    const q = form.q.value.trim();
    const tab = q.startsWith('@') ? 'users' : q.startsWith('#') ? 'tags' : (state.route.name === 'search' ? state.route.tab : 'posts');
    location.hash = `#/search?q=${encodeURIComponent(q)}&tab=${tab}`;
  },
  'admin-search': (form) => {
    location.hash = `#/admin?tab=${state.route.tab}&q=${encodeURIComponent(form.q.value.trim())}`;
  },
  edit: (form) => {
    const p = findPost(Number(form.dataset.id));
    const text = form.text.value;
    const count = charCount(text);
    if (count > MAX_TEXT || (count === 0 && !p.images?.length)) {
      showFieldError('edit-error', count > MAX_TEXT ? '1000文字以内で入力してください' : '本文を入力してください');
      form.text.focus();
      return;
    }
    p.text = text.trim();
    p.edited = true;
    closeDialog();
    render();
    toast('投稿を編集しました');
  },
  profile: (form) => {
    const name = form.name.value.trim();
    const bio = form.bio.value.trim();
    const nameBad = showFieldError('profile-name-error', [...name].length < 1 || [...name].length > 50 ? '表示名は1〜50文字で入力してください' : '');
    const bioBad = showFieldError('profile-bio-error', [...bio].length > 160 ? '自己紹介は160文字以内で入力してください' : '');
    if (nameBad) return form.name.focus();
    if (bioBad) return form.bio.focus();
    Object.assign(me(), { name, bio });
    render();
    toast('プロフィールを保存しました');
  },
  login: (form) => {
    const email = form.email.value.trim();
    const password = form.password.value;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || password.length < 8) {
      showFieldError('login-error', 'メールアドレスまたはパスワードが違います');
      form.email.focus();
      return;
    }
    login();
  },
  signup: (form) => {
    const emailBad = showFieldError('signup-email-error', /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.value.trim()) ? '' : 'メールアドレスの形で入力してください');
    const pwBad = showFieldError('signup-password-error', form.password.value.length >= 8 && form.password.value.length <= 72 ? '' : 'パスワードは8文字以上72文字以内で入力してください');
    if (emailBad) return form.email.focus();
    if (pwBad) return form.password.focus();
    state.signupSent = true;
    render();
    document.getElementById('page-title')?.focus();
  },
  onboarding: (form) => {
    const handle = form.handle.value.trim();
    const name = form.name.value.trim();
    const handleMessage = handleProblem(handle);
    const status = document.getElementById('onb-handle-status');
    status.className = handleMessage ? 'field-error' : 'field-ok';
    status.textContent = handleMessage || '使えます';
    const nameBad = showFieldError('onb-name-error', [...name].length < 1 || [...name].length > 50 ? '表示名は1〜50文字で入力してください' : '');
    if (handleMessage) return form.handle.focus();
    if (nameBad) return form.name.focus();
    Object.assign(me(), { handle, name });
    state.signupSent = false;
    login('#/');
    toast('ようこそ!');
  },
};

function handleProblem(handle) {
  if (!/^[A-Za-z0-9_]{3,15}$/.test(handle)) return 'ユーザーIDは半角英数字と_の3〜15文字で入力してください';
  if (RESERVED_HANDLES.includes(handle.toLowerCase())) return 'このユーザーIDは使えません';
  const owner = userKeyByHandle(handle);
  if (owner && owner !== ME) return 'このユーザーIDは使われています';
  return '';
}

// ---- イベントの受け取り(画面の中のすべての操作をここで受ける) ----
document.addEventListener('click', (e) => {
  // メニューの外をクリックしたら、開いているメニューを閉じる
  if (state.menuFor !== null && !e.target.closest('.menu-wrap')) {
    state.menuFor = null;
    render();
  }
  const el = e.target.closest('[data-action]');
  if (!el) return;
  const action = ACTIONS[el.dataset.action];
  if (!action) return;
  e.preventDefault();
  action(el.dataset, el);
});

document.addEventListener('submit', (e) => {
  const form = e.target.closest('[data-form]');
  if (!form) return;
  e.preventDefault();
  FORMS[form.dataset.form]?.(form);
});

let handleTimer;
document.addEventListener('input', (e) => {
  const el = e.target;
  if (el.dataset.input === 'draft') {
    state.drafts[el.dataset.kind].text = el.value;
    updateComposer(el.dataset.kind);
  } else if (el.dataset.input === 'alt') {
    state.drafts[el.dataset.kind].images[Number(el.dataset.index)].alt = el.value;
  } else if (el.dataset.input === 'handle') {
    // 入力を止めて 0.5 秒たったら、使えるかを確かめる
    clearTimeout(handleTimer);
    handleTimer = setTimeout(() => {
      const status = document.getElementById('onb-handle-status');
      const message = handleProblem(el.value.trim());
      status.className = message ? 'field-error' : 'field-ok';
      status.textContent = el.value.trim() ? message || '使えます' : '';
    }, 500);
  }
});

document.addEventListener('change', (e) => {
  const el = e.target;
  if (el.dataset.change === 'images') {
    addImages(el.dataset.kind, [...el.files]);
    el.value = '';
  } else if (el.dataset.change === 'theme') {
    state.theme = el.value;
    applyTheme();
  } else if (el.dataset.change === 'avatar' && el.files[0]) {
    const file = el.files[0];
    if (!IMAGE_TYPES.includes(file.type)) return toast('JPEG・PNG・WebP・GIF の画像を選んでください', { error: true });
    me().avatarUrl = URL.createObjectURL(file);
    render();
    toast('アイコン画像を変更しました');
  }
});

document.addEventListener('keydown', (e) => {
  // 投稿欄で Ctrl + Enter を押したら投稿する
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && e.target.dataset?.input === 'draft') {
    e.preventDefault();
    e.target.form?.requestSubmit();
  }
  // メニューは Esc で閉じ、「…」ボタンに戻る
  if (e.key === 'Escape' && state.menuFor !== null && !dialog.open) {
    const id = state.menuFor;
    state.menuFor = null;
    render();
    document.querySelector(`[data-key="more-${id}"]`)?.focus();
  }
});

// 入力欄から離れたら、待たせていた書き直しを行う
document.addEventListener('focusout', () => {
  setTimeout(() => {
    if (state.pendingRender) renderSoon();
  }, 0);
});

// 一番上まで戻ったら「新しい投稿 ○件」を消す
window.addEventListener('scroll', () => {
  if (state.newCount && window.scrollY < 100) {
    state.newCount = 0;
    renderSoon();
  }
}, { passive: true });

function applyTheme() {
  if (state.theme === 'system') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = state.theme;
}

window.addEventListener('hashchange', onRouteChange);
setInterval(receive, INCOMING_INTERVAL);
applyTheme();
onRouteChange();
