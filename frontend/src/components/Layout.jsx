import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router'
import { useAuth } from '../auth.jsx'
import Composer from './Composer.jsx'
import Icon from './Icon.jsx'
import Modal from './Modal.jsx'

/**
 * ログイン後の画面の枠(画面仕様定義書 3.1・3.2)。PC は左のメニュー、スマートフォンは下のメニューと右下の「投稿する」。
 * 画面が変わったら、画面の見出しにキーボードの位置を移し、ブラウザのタブの題名を変える。
 */
export default function Layout({ title, sub, actions, children }) {
  const { user } = useAuth()
  const heading = useRef(null)
  const [composing, setComposing] = useState(false)

  useEffect(() => {
    document.title = `${title} / SNS`
    heading.current?.focus()
  }, [title])

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
      <div className="shell">
        <nav className="side" aria-label="メインメニュー">
          <NavLink className="brand hide-sm" to="/">
            <span className="brand-mark">
              <Icon name="at" size={22} />
            </span>
            <span className="brand-label">SNS</span>
          </NavLink>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={`nav-item${item.hideSm ? ' hide-sm' : ''}`}>
              <Icon name={item.icon} size={26} />
              <span className="nav-label">{item.label}</span>
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
            <div>
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
