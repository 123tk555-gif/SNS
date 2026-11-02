import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import Avatar from '../components/Avatar.jsx'
import Icon from '../components/Icon.jsx'
import InfiniteList from '../components/InfiniteList.jsx'
import Layout from '../components/Layout.jsx'
import { STREAM_NOTIFICATION, useRealtime } from '../realtime.jsx'
import { relativeTime } from '../time.js'
import { useTimeline } from '../useTimeline.js'

const KINDS = {
  LIKE: { icon: 'heart', text: 'さんがあなたの投稿にいいねしました' },
  REPLY: { icon: 'reply', text: 'さんがあなたの投稿に返信しました' },
  FOLLOW: { icon: 'user', text: 'さんがあなたをフォローしました' },
  MENTION: { icon: 'at', text: 'さんがあなたをメンションしました' },
}

/**
 * S-09 通知(F-NOTIF-03〜05)。開いて一覧を読み込んだら、表示した通知を既読にしてバッジを 0 にする。
 * 「未読」の印は、画面を開いている間は残す(どれが新しかったか分かるように)。
 * 開いている間に届いた通知は先頭に足し、すぐに既読にする。
 */
export default function NotificationsPage() {
  const list = useTimeline('/api/notifications')
  const { prepend, posts: items, status } = list
  const { setUnreadCount } = useRealtime()
  const [fresh, setFresh] = useState(null) // 開いたときに未読だった通知の ID
  const readUpTo = useRef(0)

  /** upToId 以下の通知を既読にし、メニューのバッジを合わせる */
  const markRead = useCallback(async (upToId) => {
    if (upToId <= readUpTo.current) return
    readUpTo.current = upToId
    try {
      const result = await api('/api/notifications/read', { method: 'POST', body: { upToId } })
      setUnreadCount(result.unreadCount)
    } catch {
      // 既読にできなくても、一覧はそのまま見られる
    }
  }, [setUnreadCount])

  // 最初の読み込みが終わったら、表示した通知を既読にする(「未読」の印を付ける通知は、ここで覚えておく)
  useEffect(() => {
    if (status !== 'idle' || fresh !== null) return
    setFresh(new Set(items.filter((n) => !n.read).map((n) => n.id)))
    if (items.length) markRead(items[0].id)
    else setUnreadCount(0)
  }, [status, fresh, items, markRead, setUnreadCount])

  // 開いている間に届いた通知
  useEffect(() => {
    const onNotification = (e) => {
      const { notification } = e.detail
      prepend(notification)
      setFresh((current) => new Set([...(current ?? []), notification.id]))
      markRead(notification.id)
    }
    window.addEventListener(STREAM_NOTIFICATION, onNotification)
    return () => window.removeEventListener(STREAM_NOTIFICATION, onNotification)
  }, [prepend, markRead])

  return (
    <Layout title="通知">
      <InfiniteList {...list} emptyText="まだ通知はありません" onLoadMore={list.loadMore} onRetry={list.retry}
        renderItem={(n) => <NotificationItem key={n.id} notification={n} unread={fresh?.has(n.id) ?? !n.read} />} />
    </Layout>
  )
}

/** 通知 1 件。押すと、フォローは相手のプロフィール、それ以外は対象の投稿へ(F-NOTIF-05) */
function NotificationItem({ notification: n, unread }) {
  const kind = KINDS[n.type]
  const to = n.type === 'FOLLOW' ? `/users/${n.actor.handle}` : `/posts/${n.post.id}`
  return (
    <div className={`notif${unread ? ' unread' : ''}`}>
      <span className={`kind${n.type === 'LIKE' ? ' like' : ''}`}>
        <Icon name={kind.icon} size={28} filled={n.type === 'LIKE'} />
      </span>
      <Avatar user={n.actor} size="sm" />
      <Link className="body" to={to}>
        <span>
          {unread && <span className="unread-label">未読</span>}
          <strong>{n.actor.displayName}</strong>
          {kind.text}
        </span>
        {n.post && <span className="preview">{n.post.textPreview}</span>}
        <span className="when">{relativeTime(n.createdAt)}</span>
      </Link>
    </div>
  )
}
