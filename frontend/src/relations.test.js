import { describe, expect, test } from 'vitest'
import { relationMessage } from './relations.js'

// ミュート・ブロックのお知らせの文(画面仕様定義書 S-07)
describe('relationMessage', () => {
  test('ミュートしたときは、タイムラインに出なくなることも伝える', () => {
    expect(relationMessage('hanako', 'mute', true)).toBe('@hanako さんをミュートしました。このユーザーの投稿はタイムラインに表示されません')
  })

  test('解除・ブロックの文', () => {
    expect(relationMessage('hanako', 'mute', false)).toBe('@hanako さんのミュートを解除しました')
    expect(relationMessage('ken', 'block', true)).toBe('@ken さんをブロックしました')
    expect(relationMessage('ken', 'block', false)).toBe('@ken さんのブロックを解除しました')
  })
})
