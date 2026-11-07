import { afterEach, describe, expect, test, vi } from 'vitest'
import { loadTheme } from './theme.js'

// 表示モード(NF-A11Y-05)の読み込み。ブラウザの保存(localStorage)が使えないときも、こわれずに既定に戻る
describe('loadTheme', () => {
  afterEach(() => vi.unstubAllGlobals())
  const storage = (value) => ({ getItem: () => value, setItem() {}, removeItem() {} })

  test('保存した値を読む', () => {
    vi.stubGlobal('localStorage', storage('dark'))
    expect(loadTheme()).toBe('dark')
  })

  test('保存していなければ「端末に合わせる」', () => {
    vi.stubGlobal('localStorage', storage(null))
    expect(loadTheme()).toBe('system')
  })

  test('知らない値が保存されていたら「端末に合わせる」', () => {
    vi.stubGlobal('localStorage', storage('purple'))
    expect(loadTheme()).toBe('system')
  })

  test('保存が使えないブラウザ(プライベートモードなど)でも「端末に合わせる」', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('SecurityError') } })
    expect(loadTheme()).toBe('system')
  })
})
