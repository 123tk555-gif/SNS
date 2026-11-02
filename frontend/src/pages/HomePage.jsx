import { useEffect } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import Composer, { POST_CREATED } from '../components/Composer.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { useNotice } from '../notice.jsx'
import { useTimeline } from '../useTimeline.js'

/** S-05 ホーム。「すべて」「フォロー中」のタイムラインと投稿欄。新着の自動反映は M6 で作る */
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
      <Timeline key={tab} tab={tab} />
    </Layout>
  )
}

/** tab: all(すべて。F-TL-01)/ following(自分とフォローしている人の投稿。F-TL-02) */
function Timeline({ tab }) {
  const timeline = useTimeline(`/api/timeline?tab=${tab}`)
  const { prepend } = timeline

  // 自分が投稿したら(ダイアログからの投稿も)、先頭に足す。返信はタイムラインに出さない(F-TL-06)
  useEffect(() => {
    const onCreated = (e) => {
      if (!e.detail.replyTo) prepend(e.detail)
    }
    window.addEventListener(POST_CREATED, onCreated)
    return () => window.removeEventListener(POST_CREATED, onCreated)
  }, [prepend])

  const empty = tab === 'all' ? (
    'まだ投稿がありません。最初の投稿をしてみましょう'
  ) : (
    <>
      <p>フォローしている人の投稿がここに表示されます</p>
      <Link className="btn btn-primary" to="/search">
        ユーザーを探す
      </Link>
    </>
  )

  return (
    <PostList {...timeline} emptyText={empty} onLoadMore={timeline.loadMore} onRetry={timeline.retry}
      onChange={timeline.replace} onDelete={timeline.remove} />
  )
}
