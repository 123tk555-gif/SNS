// 投稿日時の表示(画面仕様定義書 3.4)。ブラウザ標準の Intl を使う(追加の部品は使わない)

const MINUTE = 60 * 1000
const timeFormat = new Intl.DateTimeFormat('ja-JP', { hour: 'numeric', minute: '2-digit' })

/** 「たった今」「3分前」「5時間前」「10月25日」「2025年10月25日」 */
export function relativeTime(iso, now = Date.now()) {
  const date = new Date(iso)
  const minutes = Math.floor((now - date.getTime()) / MINUTE)
  if (minutes < 1) return 'たった今'
  if (minutes < 60) return `${minutes}分前`
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}時間前`
  const monthDay = `${date.getMonth() + 1}月${date.getDate()}日`
  return date.getFullYear() === new Date(now).getFullYear() ? monthDay : `${date.getFullYear()}年${monthDay}`
}

/** 「2026年11月1日 12:15」 */
export function fullTime(iso) {
  const date = new Date(iso)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${timeFormat.format(date)}`
}
