import { useEffect } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import Composer, { POST_CREATED } from '../components/Composer.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { useNotice } from '../notice.jsx'
import { useTimeline } from '../useTimeline.js'

/** S-05 ホーム。「すべて」のタイムラインと投稿欄。「フォロー中」は M5、新着の自動反映は M6 で作る */
export default function HomePage() {
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') === 'following' ? 'following' : 'all'
  const location = useLocation()
  const notify = useNotice()

  // 初期設定のあとの「ようこそ!」など、前の画面からのお知らせ
  useEffect(() => {
    if (location.state?.notice) notify(location.state.notice)
  }, [location.state, notify])

  return (
    <Layout title="ホーム">
      <nav className="tabs" aria-label="タイムラインの種類">
        <Link className="tab" to="/" aria-current={tab === 'all' ? 'page' : undefined}>
          すべて
        </Link>
        <Link className="tab" to="/?tab=following" aria-current={tab === 'following' ? 'page' : undefined}>
          フォロー中
        </Link>
      </nav>
      <Composer id="home" />
      {tab === 'all' ? (
        <AllTimeline />
      ) : (
        <div className="empty">「フォロー中」のタイムラインは、フォロー機能(M5)と一緒に作ります。</div>
      )}
    </Layout>
  )
}

function AllTimeline() {
  const timeline = useTimeline('/api/timeline?tab=all')
  const { prepend } = timeline

  // 自分が投稿したら(ダイアログからの投稿も)、先頭に足す
  useEffect(() => {
    const onCreated = (e) => prepend(e.detail)
    window.addEventListener(POST_CREATED, onCreated)
    return () => window.removeEventListener(POST_CREATED, onCreated)
  }, [prepend])

  return (
    <PostList {...timeline} emptyText="まだ投稿がありません。最初の投稿をしてみましょう"
      onLoadMore={timeline.loadMore} onRetry={timeline.retry} onChange={timeline.replace} onDelete={timeline.remove} />
  )
}
