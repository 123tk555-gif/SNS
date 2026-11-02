import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import Layout from '../components/Layout.jsx'
import PostCard from '../components/PostCard.jsx'
import TrendList from '../components/TrendList.jsx'
import { useNotice } from '../notice.jsx'

/**
 * S-11 トレンド(F-TREND-01〜03)。直近 24 時間のハッシュタグ(10 位まで)と人気の投稿(10 件まで)。
 * 画面を開いたときに読み込み、自動では更新しない(「更新」ボタンで読み込み直す)
 */
export default function TrendsPage() {
  const notify = useNotice()
  const [trends, setTrends] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      setTrends(await api('/api/trends'))
      return true
    } catch (e) {
      setError(e.message)
      return false
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const refresh = (
    <button className="btn btn-outline" type="button" onClick={async () => (await load()) && notify('トレンドを更新しました')}>
      更新
    </button>
  )

  return (
    <Layout title="トレンド" sub="直近24時間" actions={refresh}>
      {error && (
        <div className="empty">
          <p className="field-error">エラー: {error}</p>
          <button className="btn btn-outline" type="button" onClick={load}>
            もう一度読み込む
          </button>
        </div>
      )}
      {!trends && !error && (
        <p className="list-end" role="status">
          読み込み中…
        </p>
      )}
      {trends && (
        <>
          <h2 className="section-title">ハッシュタグ</h2>
          <TrendList tags={trends.tags} ranked />
          <h2 className="section-title">人気の投稿</h2>
          {trends.popularPosts.length ? (
            trends.popularPosts.map((post) => (
              <PostCard key={post.id} post={post}
                onChange={(changed) => setTrends((t) => ({ ...t, popularPosts: t.popularPosts.map((p) => (p.id === changed.id ? changed : p)) }))}
                onDelete={(id) => setTrends((t) => ({ ...t, popularPosts: t.popularPosts.filter((p) => p.id !== id) }))} />
            ))
          ) : (
            <p className="empty">直近24時間の人気の投稿はまだありません</p>
          )}
        </>
      )}
    </Layout>
  )
}
