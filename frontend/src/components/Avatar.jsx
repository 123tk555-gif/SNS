import { Link } from 'react-router'

// アイコン画像がない人の色。白い文字との色の差が十分にある色だけを使う
const COLORS = ['#1d4ed8', '#b4410c', '#047857', '#6d28d9', '#be185d', '#0f766e', '#a21caf', '#9a3412']

/** 利用者のアイコン。画像がなければ、表示名の 1 文字目を色付きの丸で表示する。押すとプロフィールへ */
export default function Avatar({ user, size = '' }) {
  const style = user.avatarUrl
    ? { background: `#ccc url("${user.avatarUrl}") center / cover` }
    : { background: COLORS[user.id % COLORS.length] }
  return (
    <Link className={`avatar ${size}`} to={`/users/${user.handle}`} style={style}
      aria-label={`${user.displayName}さんのプロフィール`}>
      {user.avatarUrl ? '' : [...(user.displayName ?? '?')][0]}
    </Link>
  )
}
