// 画面の操作テスト(Playwright)で共通に使うもの
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

/** スクリーンショットとテスト用の画像の置き場(git に入れない) */
export const out = join(dirname(fileURLToPath(import.meta.url)), 'output').replaceAll('\\', '/');
mkdirSync(out, { recursive: true });

/** 単色の PNG を作る(縮小・アイコン画像の確認に使う) */
export function png(width, height) {
  const crcTable = [...Array(256)].map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 2;
  const row = Buffer.alloc(1 + width * 3, 0); for (let x = 0; x < width; x++) { row[1 + x * 3] = 200; row[2 + x * 3] = 120; row[3 + x * 3] = 60; }
  const raw = Buffer.concat(Array(height).fill(row));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

/** テスト用の画像ファイルを作って、そのパスを返す */
export function fixture(name, width, height) {
  const path = `${out}/${name}`;
  writeFileSync(path, png(width, height));
  return path;
}

/**
 * タイムラインなどのタブを押し、そのタブの一覧が出るまで待つ。
 * 押した直後は前のタブの一覧がまだ見えていることがあるので(遅い PC や CI)、
 * タブが選ばれた状態(aria-current)に変わってから、新しい一覧(投稿か「ありません」)を待つ
 */
export async function openTab(page, label) {
  await page.click(`a.tab:has-text("${label}")`);
  await page.waitForSelector(`a.tab[aria-current="page"]:has-text("${label}")`);
  await page.waitForSelector('main .empty, main article.card');
}
