import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, test } from 'vitest'
import RichText, { tagName } from './RichText.jsx'

// 本文の #タグ・@ユーザーID・URL のリンク化(機能定義書 3.3 の抽出ルール)。画面を出さず、HTML の文字列で確かめる
const html = (text) => renderToStaticMarkup(
  <MemoryRouter>
    <RichText text={text} />
  </MemoryRouter>,
)

describe('RichText', () => {
  test('#タグは、そのタグの投稿一覧へのリンクになる(全角・大文字はそろえる)', () => {
    expect(html('今日は #SpringBoot')).toMatch(/<a href="\/tags\/springboot"[^>]*>#SpringBoot<\/a>/)
    expect(html('#カフェ巡り')).toContain(`href="/tags/${encodeURIComponent('カフェ巡り')}"`)
  })

  test('数字だけのタグと、前が英数字のものはリンクにしない', () => {
    expect(html('#123')).not.toContain('<a')
    expect(html('abc#tag')).not.toContain('<a')
  })

  test('@ユーザーID はプロフィールへのリンクになる', () => {
    expect(html('@hanako さん')).toMatch(/<a href="\/users\/hanako"[^>]*>@hanako<\/a>/)
  })

  test('URL は新しいタブで開くリンクにし、そのことを読み上げソフトに伝える', () => {
    const out = html('見て https://example.com/a?b=1 です')
    expect(out).toContain('href="https://example.com/a?b=1"')
    expect(out).toContain('target="_blank"')
    expect(out).toContain('rel="noopener noreferrer"')
    expect(out).toContain('(新しいタブで開きます)')
  })

  test('HTML は文字として表示され、スクリプトにならない', () => {
    const out = html('<img src=x onerror=alert(1)>')
    expect(out).not.toContain('<img')
    expect(out).toContain('&lt;img src=x onerror=alert(1)&gt;')
  })

  test('javascript: などは URL として扱わない(http と https だけ)', () => {
    expect(html('javascript:alert(1)')).not.toContain('<a')
  })
})

describe('tagName', () => {
  test('全角の英数字と大文字を、そろえた形にする', () => {
    expect(tagName('ＳｐｒｉｎｇＢｏｏｔ')).toBe('springboot')
  })
})
