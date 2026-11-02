import { createContext, useContext, useEffect, useState } from 'react'
import { api } from './api.js'
import { useAuth } from './auth.jsx'

// リアルタイム配信(機能定義書 3.8)。ログイン中は /api/stream につなぎっぱなしにし、
// 届いたイベントを画面全体に知らせる(window の CustomEvent)。通知の未読の数もここで持つ。
export const STREAM_POST_CREATED = 'sns:stream-post-created' // detail: { post, followedByMe }
export const STREAM_POST_DELETED = 'sns:stream-post-deleted' // detail: { id }
export const STREAM_NOTIFICATION = 'sns:stream-notification' // detail: { unreadCount, notification }
export const STREAM_RECONNECTED = 'sns:stream-reconnected' // つなぎ直した(取りこぼしを取り直す合図)

const RealtimeContext = createContext({ unreadCount: 0, setUnreadCount: () => {} })

export function RealtimeProvider({ children }) {
  const { status, user, refresh } = useAuth()
  const [unreadCount, setUnreadCount] = useState(0)
  const active = status === 'authenticated' && user.onboarded

  // ログインした・ログイン状態を確かめ直したときの未読の数
  useEffect(() => {
    if (active) setUnreadCount(user.unreadNotificationCount)
  }, [active, user])

  useEffect(() => {
    if (!active) return undefined
    const source = new EventSource('/api/stream')
    let lostConnection = false
    const forward = (name) => (e) => window.dispatchEvent(new CustomEvent(name, { detail: JSON.parse(e.data) }))

    source.addEventListener('post-created', forward(STREAM_POST_CREATED))
    source.addEventListener('post-deleted', forward(STREAM_POST_DELETED))
    source.addEventListener('notification', (e) => {
      const detail = JSON.parse(e.data)
      setUnreadCount(detail.unreadCount)
      window.dispatchEvent(new CustomEvent(STREAM_NOTIFICATION, { detail }))
    })
    source.onopen = () => {
      // 切れたあとにつなぎ直せたら、その間の取りこぼしを取り直す
      if (lostConnection) {
        lostConnection = false
        window.dispatchEvent(new CustomEvent(STREAM_RECONNECTED))
        api('/api/notifications/unread-count').then((r) => setUnreadCount(r.unreadCount)).catch(() => {})
      }
    }
    source.onerror = () => {
      lostConnection = true
      // ブラウザが自動でつなぎ直さない(ログアウト・利用停止などで断られた)ときは、ログイン状態を確かめ直す
      if (source.readyState === EventSource.CLOSED) refresh()
    }
    return () => source.close()
  }, [active, refresh])

  return <RealtimeContext.Provider value={{ unreadCount, setUnreadCount }}>{children}</RealtimeContext.Provider>
}

export function useRealtime() {
  return useContext(RealtimeContext)
}
