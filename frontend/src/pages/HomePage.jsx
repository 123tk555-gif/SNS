import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Composer, { POST_CREATED } from '../components/Composer.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { useNotice } from '../notice.jsx'
import { STREAM_POST_CREATED, STREAM_RECONNECTED } from '../realtime.jsx'
import { useTimeline } from '../useTimeline.js'

/** S-05 ホーム。「すべて」「フォロー中」のタイムラインと投稿欄 */
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

/** これより下までスクロールしていたら「読んでいる途中」とみなす */
const SCROLLED = 200

/**
 * tab: all(すべて。F-TL-01)/ following(自分とフォローしている人の投稿。F-TL-02)。
 * 新着は再読み込みしなくても先頭に足す(F-TL-04)。下を読んでいる途中なら、見ている投稿が動かないように
 * スクロールの位置を合わせ、「↑ 新しい投稿 ○件」ボタンを出す(F-TL-05)
 */
function Timeline({ tab }) {
  const { user } = useAuth()
  const timeline = useTimeline(`/api/timeline?tab=${tab}`, { posts: true })
  const { prepend, retry, posts } = timeline
  const [newCount, setNewCount] = useState(0)
  const keepPosition = useRef(null) // 足す前のページの高さ(足したあとで、その差だけスクロールする)
  const topId = useRef(null)
  topId.current = posts[0]?.id ?? null

  // 自分が投稿したら(ダイアログからの投稿も)、先頭に足す。返信はタイムラインに出さない(F-TL-06)
  useEffect(() => {
    const onCreated = (e) => {
      if (!e.detail.replyTo) prepend(e.detail)
    }
    window.addEventListener(POST_CREATED, onCreated)
    return () => window.removeEventListener(POST_CREATED, onCreated)
  }, [prepend])

  // ほかの人の新しい投稿(SSE)
  useEffect(() => {
    const onStream = (e) => {
      const { post, followedByMe } = e.detail
      if (tab === 'following' && !followedByMe && post.author.id !== user.id) return
      if (window.scrollY > SCROLLED) {
        keepPosition.current = document.documentElement.scrollHeight
        setNewCount((n) => n + 1)
      }
      prepend({ ...post, isNew: true })
    }
    window.addEventListener(STREAM_POST_CREATED, onStream)
    return () => window.removeEventListener(STREAM_POST_CREATED, onStream)
  }, [tab, user.id, prepend])

  // つなぎ直したら、その間に増えた投稿を取り込む(多すぎるときは読み込み直す。機能定義書 3.5)
  useEffect(() => {
    const onReconnected = async () => {
      if (topId.current === null) return retry()
      try {
        const page = await api(`/api/timeline?tab=${tab}&after=${topId.current}`)
        if (page.nextCursor !== null) return retry()
        if (page.items.length) prepend(...page.items.toReversed().map((p) => ({ ...p, isNew: true })))
      } catch {
        // 次につなぎ直したときに、もう一度取り込む
      }
      return undefined
    }
    window.addEventListener(STREAM_RECONNECTED, onReconnected)
    return () => window.removeEventListener(STREAM_RECONNECTED, onReconnected)
  }, [tab, prepend, retry])

  // 新着を足したあと、足した分だけスクロールの位置を下げる(画面に描く前に行うので、見ている投稿は動かない)
  useLayoutEffect(() => {
    if (keepPosition.current !== null) {
      window.scrollBy(0, document.documentElement.scrollHeight - keepPosition.current)
      keepPosition.current = null
    }
  }, [posts])

  // 一番上まで戻ったら「新しい投稿 ○件」を消す
  useEffect(() => {
    if (!newCount) return undefined
    const onScroll = () => {
      if (window.scrollY < 100) setNewCount(0)
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [newCount])

  function scrollToTop() {
    const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' })
    setNewCount(0)
    document.querySelector('main article.card a.name')?.focus({ preventScroll: true })
  }

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
    <>
      {newCount > 0 && (
        <div className="newbar">
          <button className="btn btn-primary" type="button" onClick={scrollToTop}>
            ↑ 新しい投稿 {newCount}件
          </button>
        </div>
      )}
      <PostList {...timeline} emptyText={empty} onLoadMore={timeline.loadMore} onRetry={timeline.retry}
        onChange={timeline.replace} onDelete={timeline.remove} />
    </>
  )
}
