import { describe, expect, test } from 'vitest'
import { fullTime, relativeTime } from './time.js'

// 投稿日時の表示(画面仕様定義書 3.4)。now を引数で渡せるので、テストを流した時刻に左右されない
describe('relativeTime', () => {
  const now = new Date('2026-11-07T12:00:00+09:00').getTime()
  const ago = (ms) => new Date(now - ms).toISOString()
  const MIN = 60 * 1000

  test('1 分未満は「たった今」', () => {
    expect(relativeTime(ago(0), now)).toBe('たった今')
    expect(relativeTime(ago(59 * 1000), now)).toBe('たった今')
  })

  test('1 時間未満は「○分前」', () => {
    expect(relativeTime(ago(MIN), now)).toBe('1分前')
    expect(relativeTime(ago(59 * MIN), now)).toBe('59分前')
  })

  test('24 時間未満は「○時間前」', () => {
    expect(relativeTime(ago(60 * MIN), now)).toBe('1時間前')
    expect(relativeTime(ago(24 * 60 * MIN - 1), now)).toBe('23時間前')
  })

  test('24 時間以上前は、今年なら月日、去年より前なら年から', () => {
    expect(relativeTime('2026-10-25T09:00:00+09:00', now)).toBe('10月25日')
    expect(relativeTime('2025-10-25T09:00:00+09:00', now)).toBe('2025年10月25日')
  })

  test('年の変わり目: 1 月 1 日に見た、前の年の 12 月 31 日の投稿', () => {
    const newYear = new Date('2027-01-01T00:30:00+09:00').getTime()
    expect(relativeTime('2026-12-31T00:00:00+09:00', newYear)).toBe('2026年12月31日')
  })
})

describe('fullTime', () => {
  test('「2026年11月1日 12:15」の形', () => {
    expect(fullTime('2026-11-01T03:15:00Z')).toBe('2026年11月1日 12:15')
  })
})
