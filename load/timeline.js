// 負荷テストの台本(k6)。使い方は load/README.md。
// 仮想ユーザー(VU)を 1 → 30 人(VUS で変えられる)に 1 分かけて増やし、30 人のまま 2 分保つ。
// 各ユーザーは最初にログインし、そのあと「ホームを開く → 読む → ときどき続きを読む・検索・いいね・投稿」をくり返す。
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://sns.localhost';
// 同時に使う人数(初期値 30)。k6 の -e VUS=60 で変えられる。31 人目からは、同じユーザーで別にログインする
const VUS = Number(__ENV.VUS || 30);

export const options = {
  // Caddy が手元で作った証明書なので、証明書の確認はしない(手元のテストだけ)
  insecureSkipTLSVerify: true,
  // ログイン状態(Cookie)を、くり返しのたびに消さない(1 人のユーザーとして使い続ける)
  noCookiesReset: true,
  scenarios: {
    users: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '1m', target: VUS },
        { duration: '2m', target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  // 合格の目安: 読み込みの 95% が 800ms 以内、書き込み(投稿・いいね)の 95% が 1 秒以内、エラーが 1% 未満
  thresholds: {
    'http_req_duration{kind:read}': ['p(95)<800'],
    'http_req_duration{kind:write}': ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const read = (name) => ({ tags: { kind: 'read', name } });

function xsrf() {
  const cookies = http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'];
  return cookies ? cookies[0] : '';
}

function login() {
  // 最初の GET で、CSRF 対策の Cookie(XSRF-TOKEN)を受け取る
  http.get(`${BASE}/api/auth/config`, read('config'));
  const n = String(((__VU - 1) % 30) + 1).padStart(2, '0');
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: `load${n}@example.com`, password: 'password123' }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf() },
    tags: { kind: 'login', name: 'login' },
  });
  check(res, { 'ログインできる': (r) => r.status === 200 });
}

function post(text) {
  // 投稿は multipart/form-data(画像を付けられる形)で送る
  const boundary = `----k6${Date.now()}${__VU}`;
  const body = `--${boundary}\r\nContent-Disposition: form-data; name="text"\r\n\r\n${text}\r\n--${boundary}--\r\n`;
  return http.post(`${BASE}/api/posts`, body, {
    headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}`, 'X-XSRF-TOKEN': xsrf() },
    tags: { kind: 'write', name: 'post' },
  });
}

export default function () {
  if (__ITER === 0) login();

  // ホームを開く(画面が最初に読むもの)
  http.get(`${BASE}/api/auth/me`, read('me'));
  const tab = Math.random() < 0.5 ? 'all' : 'following';
  const timeline = http.get(`${BASE}/api/timeline?tab=${tab}`, read(`timeline_${tab}`));
  http.get(`${BASE}/api/trends`, read('trends'));
  http.get(`${BASE}/api/notifications/unread-count`, read('unread'));
  const ok = check(timeline, { 'タイムラインが読める': (r) => r.status === 200 && Array.isArray(r.json('items')) });
  sleep(1 + Math.random() * 2); // 読んでいる時間

  const page = ok ? timeline.json() : { items: [], nextCursor: null };
  const r = Math.random();
  if (r < 0.3 && page.nextCursor) {
    // 下までスクロールして、続きを読む
    const more = http.get(`${BASE}/api/timeline?tab=${tab}&before=${page.nextCursor}`, read('timeline_more'));
    check(more, { '続きが読める': (res) => res.status === 200 });
  } else if (r < 0.45) {
    const res = http.get(`${BASE}/api/search/posts?q=${encodeURIComponent('負荷テスト')}`, read('search'));
    check(res, { '検索できる': (x) => x.status === 200 });
  } else if (r < 0.75 && page.items.length > 0) {
    const target = page.items[Math.floor(Math.random() * page.items.length)];
    const res = http.put(`${BASE}/api/posts/${target.id}/like`, null, {
      headers: { 'X-XSRF-TOKEN': xsrf() },
      tags: { kind: 'write', name: 'like' },
    });
    check(res, { 'いいねできる': (x) => x.status === 200 });
  } else if (r < 0.85) {
    const res = post(`k6 からの投稿 VU${__VU} の ${__ITER} 回目 #k6`);
    check(res, { '投稿できる': (x) => x.status === 201 });
  }
  sleep(1 + Math.random() * 2);
}
