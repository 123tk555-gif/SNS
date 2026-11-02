import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { api } from '../api.js'
import Composer from '../components/Composer.jsx'
import Layout from '../components/Layout.jsx'
import PostCard from '../components/PostCard.jsx'
import PostList from '../components/PostList.jsx'
import { useTimeline } from '../useTimeline.js'

/**
 * S-06 投稿詳細(F-POST-07, 08)。返信先(あれば)・投稿本体・返信欄・返信の一覧(古い順)を表示する。
 * URL に ?reply があれば(投稿カードの「返信」から来たとき)、返信の入力欄にキーボードの位置を移す。
 */
export default function PostDetailPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const wantsReply = searchParams.has('reply')
  const navigate = useNavigate()
  const [post, setPost] = useState(null)
  const [parent, setParent] = useState(null)
  const [error, setError] = useState(null) // { notFound: true } か { message }
  const replies = useTimeline(`/api/posts/${id}/replies`, { cursorParam: 'after' })

  const load = useCallback(async () => {
    setError(null)
    try {
      const found = await api(`/api/posts/${id}`)
      setPost(found)
      setParent(found.replyTo && !found.replyTo.unavailable ? await api(`/api/posts/${found.replyTo.id}`) : null)
    } catch (e) {
      setError(e.status === 404 ? { notFound: true } : { message: e.message })
    }
  }, [id])

  useEffect(() => {
    setPost(null)
    load()
  }, [load])

  // 投稿を読み込めたら、返信の入力欄に移る(?reply のとき)
  useEffect(() => {
    if (post && wantsReply) document.getElementById('text-reply')?.focus()
  }, [post, wantsReply])

  if (error?.notFound) {
    return (
      <Layout title="ページが見つかりません" back>
        <div className="empty">
          <p>お探しの投稿は削除されたか、URL が間違っている可能性があります。</p>
          <Link className="btn btn-primary" to="/">
            ホームへ戻る
          </Link>
        </div>
      </Layout>
    )
  }

  return (
    <Layout title="投稿" back focusHeading={!wantsReply}>
      {error && (
        <div className="empty">
          <p className="field-error">エラー: {error.message}</p>
          <button className="btn btn-outline" type="button" onClick={load}>
            もう一度読み込む
          </button>
        </div>
      )}
      {!post && !error && (
        <p className="list-end" role="status">
          読み込み中…
        </p>
      )}
      {post && (
        <>
          {parent && <PostCard post={parent} onChange={setParent} onDelete={() => setParent({ ...parent, unavailable: true })} />}
          <PostCard post={post} big onChange={setPost}
            onDelete={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))} />
          {!post.unavailable && (
            <Composer id="reply" replyTo={post}
              onPosted={(reply) => {
                // 返信を最後まで読み込んでいれば、一覧の最後に足す(途中なら、続きを読み込んだときに出る)
                if (!replies.hasMore) replies.append(reply)
                setPost((current) => ({ ...current, replyCount: current.replyCount + 1 }))
              }} />
          )}
          <h2 className="section-title">返信</h2>
          <PostList {...replies} emptyText="まだ返信はありません" onLoadMore={replies.loadMore} onRetry={replies.retry}
            onChange={replies.replace}
            onDelete={(replyId) => {
              replies.remove(replyId)
              setPost((current) => ({ ...current, replyCount: Math.max(current.replyCount - 1, 0) }))
            }} />
        </>
      )}
    </Layout>
  )
}
