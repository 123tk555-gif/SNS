// k6 の 1 件ずつの記録(CSV)を、API ごと・30 秒ごとにまとめる。
// 使い方: node load/analyze.cjs load/results/run.csv [最初の何秒を除くか]
const fs = require('fs');
const lines = fs.readFileSync(process.argv[2], 'utf8').split('\n');
const head = lines[0].split(',');
const iM = head.indexOf('metric_name'), iT = head.indexOf('timestamp'), iV = head.indexOf('metric_value'), iN = head.indexOf('name');
const rows = [];
for (const l of lines.slice(1)) { const c = l.split(','); if (c[iM] === 'http_req_duration') rows.push({ t: +c[iT], v: +c[iV], n: c[iN] }); }
const t0 = Math.min(...rows.map(r => r.t)); const from = +(process.argv[3] ?? 0); rows.splice(0, rows.length, ...rows.filter(r => r.t - t0 >= from));
const pct = (a, p) => { a = [...a].sort((x, y) => x - y); return a[Math.min(a.length - 1, Math.ceil(p / 100 * a.length) - 1)]; };
const fmt = (a) => `件数 ${String(a.length).padStart(4)}  中央 ${pct(a,50).toFixed(0).padStart(4)}ms  95% ${pct(a,95).toFixed(0).padStart(5)}ms  最大 ${Math.max(...a).toFixed(0).padStart(5)}ms`;
const by = {}; for (const r of rows) (by[r.n] ??= []).push(r.v);
console.log('--- API ごと'); for (const [n, a] of Object.entries(by).sort()) console.log(n.padEnd(18), fmt(a));
console.log('--- 30 秒ごと(全 API)');
const tb = {}; for (const r of rows) (tb[Math.floor((r.t - t0) / 30)] ??= []).push(r.v);
for (const [b, a] of Object.entries(tb)) console.log(`${b * 30}〜${b * 30 + 30} 秒`.padEnd(14), fmt(a));
