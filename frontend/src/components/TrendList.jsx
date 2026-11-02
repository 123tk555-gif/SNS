import { Link } from 'react-router'

/**
 * ハッシュタグの一覧(トレンド・検索結果)。押すとそのタグの投稿一覧へ(F-TREND-03)。
 * ranked: 順位を出す
 */
export default function TrendList({ tags, ranked = false, emptyText = '直近24時間のトレンドはまだありません' }) {
  if (!tags.length) return <p className="empty">{emptyText}</p>
  return (
    <ol className="trend-list">
      {tags.map((tag, i) => (
        <li key={tag.name}>
          <Link to={`/tags/${encodeURIComponent(tag.name)}`}>
            {ranked && <span className="rank">{i + 1}</span>}
            <span>
              <span className="tag">#{tag.name}</span>
              <br />
              <span className="count">{tag.postCount}件の投稿</span>
            </span>
          </Link>
        </li>
      ))}
    </ol>
  )
}
