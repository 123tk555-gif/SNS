import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Composer, { POST_CREATED } from '../components/Composer.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { useNotice } from '../notice.jsx'
import { STREAM_POST_CREATED, STREAM_POST_DELETED, STREAM_RECONNECTED } from '../realtime.jsx'
import { RELATION_CHANGED } from '../relations.js'
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
const SCROLLED = 100

/**
 * tab: all(すべて。F-TL-01)/ following(自分とフォローしている人の投稿。F-TL-02)。
 * ほかの人の新着(SSE)は、すぐには足さずにためておき、上部の「新しい投稿が ○ 件あります」ボタンで知らせる。
 * 押すと、ためた投稿を先頭に足して一番上へ移る(F-TL-04, F-TL-05)。読んでいる途中で一覧が動かない。
 * 自分の投稿は、すぐに先頭に足す
 */
function Timeline({ tab }) {
  const { user } = useAuth()
  const timeline = useTimeline(`/api/timeline?tab=${tab}`, { posts: true })
  const { prepend, retry, posts } = timeline
  // まだ表示していない新着(新しいものが先頭)と、多すぎて取り込めなかったか(押したら読み込み直す)
  const [pending, setPending] = useState([])
  const [overflow, setOverflow] = useState(false)
  // ボタンを出したときに一番上を見ていたら、ボタンの分の場所を空けて出す(重ねると 1 件目が隠れる)。
  // 読んでいる途中なら、場所を空けずに画面の上部に浮かせる(見ている投稿を動かさない)
  const [inline, setInline] = useState(false)
  const shown = useRef(false)
  shown.current = pending.length > 0 || overflow
  // 手元にある一番新しい投稿の ID(つなぎ直したときに、これより新しいものを取りに行く)
  const newestId = useRef(null)
  newestId.current = pending[0]?.id ?? posts[0]?.id ?? null

  const addPending = useCallback((...items) => {
    if (!shown.current) setInline(window.scrollY < SCROLLED)
    setPending((current) => {
      const known = new Set(current.map((p) => p.id))
      return [...items.filter((p) => !known.has(p.id)), ...current]
    })
  }, [])

  // 自分が投稿したら(ダイアログからの投稿も)、すぐ先頭に足す。返信はタイムラインに出さない(F-TL-06)
  useEffect(() => {
    const onCreated = (e) => {
      if (!e.detail.replyTo) prepend(e.detail)
    }
    window.addEventListener(POST_CREATED, onCreated)
    return () => window.removeEventListener(POST_CREATED, onCreated)
  }, [prepend])

  // ほかの人の新しい投稿(SSE)はためる(SSE は投稿者本人には届かない)
  useEffect(() => {
    const onStream = (e) => {
      const { post, followedByMe } = e.detail
      if (tab === 'following' && !followedByMe) return
      addPending({ ...post, isNew: true })
    }
    window.addEventListener(STREAM_POST_CREATED, onStream)
    return () => window.removeEventListener(STREAM_POST_CREATED, onStream)
  }, [tab, addPending])

  // ためている間に削除・非表示にされた投稿と、ミュート・ブロックした人の投稿は、ためた分からも外す
  useEffect(() => {
    const onDeleted = (e) => setPending((current) => current.filter((p) => p.id !== e.detail.id))
    const onRelation = (e) => {
      if (e.detail.on) setPending((current) => current.filter((p) => p.author.id !== e.detail.userId))
    }
    window.addEventListener(STREAM_POST_DELETED, onDeleted)
    window.addEventListener(RELATION_CHANGED, onRelation)
    return () => {
      window.removeEventListener(STREAM_POST_DELETED, onDeleted)
      window.removeEventListener(RELATION_CHANGED, onRelation)
    }
  }, [])

  // つなぎ直したら、その間に増えた投稿もためる(多すぎるときは、押したときに読み込み直す。機能定義書 3.5)
  useEffect(() => {
    const onReconnected = async () => {
      if (newestId.current === null) return retry()
      try {
        const page = await api(`/api/timeline?tab=${tab}&after=${newestId.current}`)
        if (page.nextCursor !== null) {
          if (!shown.current) setInline(window.scrollY < SCROLLED)
          return setOverflow(true)
        }
        const others = page.items.filter((p) => p.author.id !== user.id)
        prepend(...page.items.filter((p) => p.author.id === user.id).toReversed())
        if (others.length) addPending(...others.toReversed().map((p) => ({ ...p, isNew: true })))
      } catch {
        // 次につなぎ直したときに、もう一度取り込む
      }
      return undefined
    }
    window.addEventListener(STREAM_RECONNECTED, onReconnected)
    return () => window.removeEventListener(STREAM_RECONNECTED, onReconnected)
  }, [tab, user.id, prepend, retry, addPending])

  function showNew() {
    if (overflow) {
      retry()
    } else {
      prepend(...pending)
    }
    setPending([])
    setOverflow(false)
    const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' })
    // キーボードで使う人のために、先頭の投稿へ移る(ボタンは消えるため)
    requestAnimationFrame(() => document.querySelector('main article.card a.name')?.focus({ preventScroll: true }))
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
      {(pending.length > 0 || overflow) && (
        <div className={`newbar${inline ? ' inline' : ''}`}>
          <button className="btn btn-primary" type="button" onClick={showNew}>
            ↑ {overflow ? '新しい投稿があります' : `新しい投稿が ${pending.length} 件あります`}
          </button>
        </div>
      )}
      <PostList {...timeline} emptyText={empty} onLoadMore={timeline.loadMore} onRetry={timeline.retry}
        onChange={timeline.replace} onDelete={timeline.remove} />
    </>
  )
}
