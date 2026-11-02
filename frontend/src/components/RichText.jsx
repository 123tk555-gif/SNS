import { Link } from 'react-router'

// 本文の #ハッシュタグ・@ユーザーID・URL をリンクにする(F-POST-04。ルールは機能定義書 3.3)。
// 本文は HTML として扱わず、文字として表示する(React が自動で文字にする。NF-SEC-07)
const PATTERN = /(#[\p{L}\p{N}_ー]{1,50}|@[A-Za-z0-9_]{3,15}|https?:\/\/[^\s]+)/gu

export function tagName(text) {
  return text.normalize('NFKC').toLowerCase()
}

export default function RichText({ text }) {
  const parts = []
  let last = 0
  for (const match of text.matchAll(PATTERN)) {
    const token = match[0]
    const before = text[match.index - 1]
    if (match.index > last) parts.push(text.slice(last, match.index))
    if (token.startsWith('#') && !/^#\d+$/.test(token) && !(before && /[A-Za-z0-9]/.test(before))) {
      parts.push(<Link key={match.index} to={`/tags/${encodeURIComponent(tagName(token.slice(1)))}`}>{token}</Link>)
    } else if (token.startsWith('@')) {
      parts.push(<Link key={match.index} to={`/users/${token.slice(1)}`}>{token}</Link>)
    } else if (token.startsWith('http')) {
      parts.push(
        <a key={match.index} href={token} target="_blank" rel="noopener noreferrer">
          {token}
          <span className="sr-only">(新しいタブで開きます)</span>
        </a>,
      )
    } else {
      parts.push(token)
    }
    last = match.index + token.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return parts
}
