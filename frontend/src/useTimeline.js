import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './api.js'

/**
 * タイムラインの読み込み(機能定義書 1.5 のカーソル方式)。
 * 1 回目は新しいものから 20 件、2 回目以降は前の結果の nextCursor より古いものを取る。
 */
export function useTimeline(url) {
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
      const page = await api(before ? `${url}${separator}before=${before}` : url)
      setPosts((current) => {
        // 同じ投稿が二重に出ないようにする(新しく投稿したものが、続きの読み込みと重なることがある)
        const known = new Set(before ? current.map((p) => p.id) : [])
        return [...(before ? current : []), ...page.items.filter((p) => !known.has(p.id))]
      })
      setCursor(page.nextCursor)
      setHasMore(page.nextCursor !== null)
    } catch (e) {
      setError(e.message)
    } finally {
      loading.current = false
      setStatus('idle')
    }
  }, [url])

  useEffect(() => {
    load(null)
  }, [load])

  const loadMore = useCallback(() => {
    if (hasMore && cursor) load(cursor)
  }, [hasMore, cursor, load])

  const prepend = useCallback((post) => setPosts((current) => [post, ...current.filter((p) => p.id !== post.id)]), [])
  const replace = useCallback((post) => setPosts((current) => current.map((p) => (p.id === post.id ? post : p))), [])
  const remove = useCallback((id) => setPosts((current) => current.filter((p) => p.id !== id)), [])

  return { posts, status, hasMore, error, loadMore, retry: () => load(null), prepend, replace, remove }
}
