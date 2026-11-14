import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { useRealtime } from '../realtime.jsx'
import Composer from './Composer.jsx'
import Icon from './Icon.jsx'
import Logo from './Logo.jsx'
import Modal from './Modal.jsx'
import TrendList from './TrendList.jsx'

/**
 * ログイン後の画面の枠(画面仕様定義書 3.1・3.2)。PC は左のメニュー、スマートフォンは下のメニューと右下の「投稿する」。
 * 画面が変わったら、画面の見出しにキーボードの位置を移し、ブラウザのタブの題名を変える。
 * back: 見出しの左に「前の画面に戻る」ボタンを出す
 * focusHeading: false にすると見出しに移さない(画面の中の入力欄に移したいとき)
 */
export default function Layout({ title, sub, actions, back = false, focusHeading = true, children }) {
  const { user } = useAuth()
  const { unreadCount } = useRealtime()
  const navigate = useNavigate()
  const heading = useRef(null)
  const [composing, setComposing] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const previousUnread = useRef(unreadCount)

  useEffect(() => {
    document.title = `${title} / Leafline`
    if (focusHeading) heading.current?.focus()
  }, [title, focusHeading])

  // 未読の数が増えたら、読み上げソフトにも伝える(F-NOTIF-03)
  useEffect(() => {
    if (unreadCount > previousUnread.current) setAnnouncement(`通知が届きました。未読は ${unreadCount}件です`)
    previousUnread.current = unreadCount
  }, [unreadCount])

  const items = [
    { to: '/', label: 'ホーム', icon: 'home', end: true },
    { to: '/search', label: '検索', icon: 'search' },
    { to: '/notifications', label: '通知', icon: 'bell' },
    { to: '/trends', label: 'トレンド', icon: 'hash', hideSm: true },
    { to: '/bookmarks', label: 'ブックマーク', icon: 'bookmark', hideSm: true },
    { to: `/users/${user.handle}`, label: 'プロフィール', icon: 'user' },
    { to: '/settings', label: '設定', icon: 'settings', hideSm: true },
    ...(user.role === 'ADMIN' ? [{ to: '/admin', label: '管理者画面', icon: 'shield', hideSm: true }] : []),
  ]

  return (
    <>
      <a className="skip-link" href="#main">
        本文へ移動
      </a>
      <div className="sr-only" aria-live="polite">
        {announcement}
      </div>
      <div className="shell">
        <nav className="side" aria-label="メインメニュー">
          <NavLink className="brand hide-sm" to="/">
            <span className="brand-mark">
              <Logo size={28} />
            </span>
            <span className="brand-label">Leafline</span>
          </NavLink>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={`nav-item${item.hideSm ? ' hide-sm' : ''}`}>
              <Icon name={item.icon} size={26} />
              <span className="nav-label">{item.label}</span>
              {item.to === '/notifications' && unreadCount > 0 && (
                <>
                  <span className="badge" aria-hidden="true">
                    {unreadCount > 99 ? '99+' : unreadCount}
                  </span>
                  <span className="sr-only">(未読 {unreadCount}件)</span>
                </>
              )}
            </NavLink>
          ))}
          <button className="btn btn-primary compose-side hide-sm" type="button" aria-label="投稿する"
            onClick={() => setComposing(true)}>
            <Icon name="pen" />
            <span className="label">投稿する</span>
          </button>
        </nav>
        <main id="main" className="main">
          <div className="topbar">
            {back && (
              <button className="icon-btn plain" type="button" aria-label="前の画面に戻る"
                onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}>
                <Icon name="back" />
              </button>
            )}
            <div className="titles">
              <h1 tabIndex={-1} ref={heading}>
                {title}
              </h1>
              {sub && <div className="sub">{sub}</div>}
            </div>
            <span className="spacer" />
            {actions}
          </div>
          {children}
        </main>
        <RightColumn />
      </div>
      <button className="btn btn-primary fab" type="button" onClick={() => setComposing(true)}>
        <Icon name="pen" />
        投稿する
      </button>
      {composing && (
        <Modal title="投稿する" onClose={() => setComposing(false)} initialFocus="text-dialog">
          <Composer id="dialog" onPosted={() => setComposing(false)} />
          <div className="dialog-buttons">
            <button className="btn btn-outline" type="button" onClick={() => setComposing(false)}>
              やめる
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

/** PC(幅 1024px 以上)の右の列: 検索欄とトレンドの上位 5 件(画面仕様定義書 3.1) */
function RightColumn() {
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [tags, setTags] = useState(null)
  const [wide] = useState(() => window.matchMedia('(min-width: 1024px)').matches)

  // 右の列を出さない画面の幅(タブレット・スマートフォン)では、トレンドを取りに行かない
  useEffect(() => {
    if (!wide) return
    api('/api/trends').then((r) => setTags(r.tags.slice(0, 5))).catch(() => setTags([]))
  }, [wide])

  function submit(event) {
    event.preventDefault()
    const query = text.trim()
    if (!query) return
    const tab = /^[@＠]/.test(query) ? 'users' : /^[#＃]/.test(query) ? 'tags' : 'posts'
    navigate(`/search?q=${encodeURIComponent(query)}&tab=${tab}`)
  }

  return (
    <aside className="right" aria-label="検索とトレンド">
      <form className="panel" role="search" onSubmit={submit}>
        <label className="sr-only" htmlFor="side-search">
          検索
        </label>
        <input type="search" id="side-search" value={text} placeholder="キーワード・@ユーザーID・#タグで検索"
          onChange={(e) => setText(e.target.value)} />
      </form>
      <section className="panel">
        <h2>トレンド</h2>
        {tags ? <TrendList tags={tags} /> : <p role="status">読み込み中…</p>}
        <Link to="/trends">トレンドをすべて見る</Link>
      </section>
    </aside>
  )
}
