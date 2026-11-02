import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import { STREAM_POST_DELETED } from './realtime.jsx'

/**
 * 一覧の読み込み(機能定義書 1.5 のカーソル方式)。投稿・ユーザー・通知の一覧で使う。
 * 1 回目は最初の 20 件、2 回目以降は前の結果の nextCursor を cursorParam に渡して続きを取る。
 * cursorParam: 新しい順の一覧(タイムラインなど)は before、古い順の一覧(返信)は after
 * posts: true なら投稿の一覧。削除・非表示にされた投稿(SSE の post-deleted)を一覧から消す
 */
export function useTimeline(url, { cursorParam = 'before', posts: isPostList = false } = {}) {
  const [posts, setPosts] = useState([])
  const [cursor, setCursor] = useState(null)
  const [hasMore, setHasMore] = useState(true)
  const [status, setStatus] = useState('loading') // loading | idle
  const [error, setError] = useState('')
  const loading = useRef(false)

  const load = useCallback(async (before) => {
    if (loading.current) return
    loading.current = true
    setStatus('loading')
    setError('')
    try {
      const separator = url.includes('?') ? '&' : '?'
      const page = await api(before ? `${url}${separator}${cursorParam}=${before}` : url)
      setPosts((current) => {
        // 同じ投稿が二重に出ないようにする(新しく投稿したものが、続きの読み込みと重なることがある)
        const known = new Set(before ? current.map((p) => p.id) : [])
        return [...(before ? current : []), ...page.items.filter((p) => !known.has(p.id))]
      })
      setCursor(page.nextCursor)
      // nextCursor がない一覧(検索結果のユーザーなど)も、続きなしとして扱う
      setHasMore(page.nextCursor != null)
    } catch (e) {
      setError(e.message)
    } finally {
      loading.current = false
      setStatus('idle')
    }
  }, [url, cursorParam])

  useEffect(() => {
    load(null)
  }, [load])

  const loadMore = useCallback(() => {
    if (hasMore && cursor) load(cursor)
  }, [hasMore, cursor, load])

  /** 先頭に足す(items は新しいものが先頭の順で渡す)。同じものは足さない */
  const prepend = useCallback((...items) => setPosts((current) => {
    const ids = new Set(items.map((p) => p.id))
    return [...items, ...current.filter((p) => !ids.has(p.id))]
  }), [])
  const append = useCallback((post) => setPosts((current) => [...current.filter((p) => p.id !== post.id), post]), [])
  const replace = useCallback((post) => setPosts((current) => current.map((p) => (p.id === post.id ? post : p))), [])
  const remove = useCallback((id) => setPosts((current) => current.filter((p) => p.id !== id)), [])

  // ほかの人が投稿を削除した・管理者が非表示にしたら、開いている一覧からも消す
  useEffect(() => {
    if (!isPostList) return undefined
    const onDeleted = (e) => remove(e.detail.id)
    window.addEventListener(STREAM_POST_DELETED, onDeleted)
    return () => window.removeEventListener(STREAM_POST_DELETED, onDeleted)
  }, [isPostList, remove])

  return { posts, status, hasMore, error, loadMore, retry: () => load(null), prepend, append, replace, remove }
}
