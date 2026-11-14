import { readFileSync } from 'node:fs'
import { describe, expect, test } from 'vitest'

// 色の組み合わせの見やすさ(画面仕様定義書 3.7)。styles.css の色の変数を読み、文字と背景の明るさの差(コントラスト比)を計算する。
// 色を変えたときに、読みにくい組み合わせになっていれば、ここで失敗する
const css = readFileSync(new URL('./styles.css', import.meta.url), 'utf8')

function variables(selector) {
  const start = css.indexOf(`${selector} {`)
  if (start < 0) throw new Error(`${selector} が見つかりません`)
  const block = css.slice(start, css.indexOf('}', start))
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})\s*;/gi)].map((m) => [m[1], m[2]]))
}

function luminance(hex) {
  const [r, g, b] = hex.match(/\w\w/g).map((h) => parseInt(h, 16) / 255)
    .map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a, b) {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (light + 0.05) / (dark + 0.05)
}

const themes = {
  ライト: variables(':root'),
  ダーク: variables(':root:not([data-theme="light"])'),
}

test('ダークの色は、端末の設定と、設定画面で選んだときとで同じ', () => {
  expect(variables(':root[data-theme="dark"]')).toEqual(themes.ダーク)
})

describe.each(Object.entries(themes))('%s', (_, c) => {
  // 文字が載る背景: 投稿・中央の列(bg)、ページの背景(page)、見出しの帯(topbar)、メニューの今いる項目(nav-active)、薄い背景(surface)
  const backgrounds = ['bg', 'page', 'topbar', 'nav-active', 'surface', 'accent-soft']

  test.each(backgrounds)('ふつうの文字は、%s の上で 7 以上', (bg) => {
    expect(contrast(c.text, c[bg])).toBeGreaterThanOrEqual(7)
  })

  test.each(backgrounds)('薄い文字(日時・件数など)は、%s の上で 4.5 以上', (bg) => {
    expect(contrast(c.muted, c[bg])).toBeGreaterThanOrEqual(4.5)
  })

  test.each(backgrounds)('リンク・タブの印(アクセント)は、%s の上で 4.5 以上', (bg) => {
    expect(contrast(c.accent, c[bg])).toBeGreaterThanOrEqual(4.5)
  })

  test('リンク(アクセント)は、投稿の白い背景の上で 7 以上', () => {
    expect(contrast(c.accent, c.bg)).toBeGreaterThanOrEqual(7)
  })

  test('ボタンの文字は、アクセントの色の上で 4.5 以上', () => {
    expect(contrast(c['on-accent'], c.accent)).toBeGreaterThanOrEqual(4.5)
  })

  test.each(backgrounds)('入力欄・ボタンの枠は、%s の上で 3 以上', (bg) => {
    expect(contrast(c['control-border'], c[bg])).toBeGreaterThanOrEqual(3)
  })

  test('いいね・削除の色は、投稿の背景の上で 4.5 以上', () => {
    expect(contrast(c.like, c.bg)).toBeGreaterThanOrEqual(4.5)
    expect(contrast(c.danger, c.bg)).toBeGreaterThanOrEqual(4.5)
  })
})
