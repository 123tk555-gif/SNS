// 表示モード(画面仕様定義書 2 の「ライトモード・ダークモード」、NF-A11Y-05)。
// 'system'(端末に合わせる)・'light'・'dark' をブラウザに保存し、<html data-theme> に反映する。
// 'system' のときは data-theme を付けず、CSS の prefers-color-scheme に任せる

const KEY = 'sns.theme'
export const THEMES = [
  ['system', '端末に合わせる'],
  ['light', 'ライト'],
  ['dark', 'ダーク'],
]

export function loadTheme() {
  try {
    const value = localStorage.getItem(KEY)
    return THEMES.some(([key]) => key === value) ? value : 'system'
  } catch {
    return 'system' // 保存が使えないブラウザ(プライベートモードなど)
  }
}

export function applyTheme(theme) {
  if (theme === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}

export function saveTheme(theme) {
  applyTheme(theme)
  try {
    if (theme === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, theme)
  } catch {
    // 保存できなくても、開いている間は切り替わっている
  }
}
