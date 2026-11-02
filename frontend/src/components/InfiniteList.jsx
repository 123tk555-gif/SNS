import { useEffect, useRef } from 'react'

/**
 * 続きを読み込める一覧と、一覧の状態の表示(画面仕様定義書 3.4)。投稿の一覧とユーザーの一覧で使う。
 * 下端が見えたら続きを自動で読み込み、キーボードの利用者のために「さらに読み込む」ボタンも置く(F-TL-03)。
 * renderItem(item): 1 件分の表示
 */
export default function InfiniteList({ posts: items, status, hasMore, error, emptyText, onLoadMore, onRetry, renderItem }) {
  const sentinel = useRef(null)

  useEffect(() => {
    if (!hasMore || status !== 'idle' || !sentinel.current) return undefined
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) onLoadMore()
    }, { rootMargin: '300px 0px' })
    observer.observe(sentinel.current)
    return () => observer.disconnect()
  }, [hasMore, status, onLoadMore])

  if (status === 'loading' && items.length === 0) {
    return (
      <p className="list-end" role="status">
        読み込み中…
      </p>
    )
  }
  if (error && items.length === 0) {
    return (
      <div className="empty">
        <p className="field-error">エラー: {error}</p>
        <button className="btn btn-outline" type="button" onClick={onRetry}>
          もう一度読み込む
        </button>
      </div>
    )
  }
  if (items.length === 0) return <div className="empty">{emptyText}</div>

  return (
    <>
      {items.map(renderItem)}
      {hasMore ? (
        <div className="list-more" ref={sentinel}>
          <p className="list-end" role="status">
            {status === 'loading' ? '読み込み中…' : error ? `エラー: ${error}` : ''}
          </p>
          <button className="btn btn-outline" type="button" onClick={onLoadMore} disabled={status === 'loading'}>
            さらに読み込む
          </button>
        </div>
      ) : (
        <p className="list-end">これ以上はありません</p>
      )}
    </>
  )
}
