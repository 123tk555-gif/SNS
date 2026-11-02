import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { useNotice } from '../notice.jsx'
import { fullTime, relativeTime } from '../time.js'
import Avatar from './Avatar.jsx'
import Icon from './Icon.jsx'
import Modal from './Modal.jsx'
import RichText from './RichText.jsx'

/**
 * 投稿カード(画面仕様定義書 3.4)。自分の投稿は「…」から編集・削除できる。
 * いいね・ブックマークは M4 で加える。
 * onChange(post): 編集したとき / onDelete(id): 削除したとき
 */
export default function PostCard({ post, onChange, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [dialog, setDialog] = useState(null) // 'edit' | 'delete' | { image: 番号 }
  const menuRef = useRef(null)

  // メニューの外を押す・Esc キーで、メニューを閉じる
  useEffect(() => {
    if (!menuOpen) return undefined
    const close = (e) => {
      if (e.type === 'keydown' ? e.key === 'Escape' : !menuRef.current?.contains(e.target)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [menuOpen])

  if (post.unavailable) {
    return (
      <div className="card">
        <p className="text">この投稿は削除されました</p>
      </div>
    )
  }

  const shown = post.images.slice(0, 4)

  return (
    <article className="card" aria-label={`${post.author.displayName}さんの投稿`}>
      <Avatar user={post.author} />
      <div className="card-body">
        <div className="meta">
          <Link className="name" to={`/users/${post.author.handle}`}>
            {post.author.displayName}
          </Link>
          <span className="handle">@{post.author.handle}</span>
          <Link className="time" to={`/posts/${post.id}`} title={fullTime(post.createdAt)}>
            <time dateTime={post.createdAt}>{relativeTime(post.createdAt)}</time>
          </Link>
          {post.editedAt && <span className="edited">(編集済み)</span>}
        </div>
        {post.hidden && <p className="notice-hidden">管理者により非表示になりました</p>}
        {post.text && (
          <p className="text">
            <RichText text={post.text} />
          </p>
        )}
        {shown.length > 0 && (
          <div className={`imgs n${Math.min(shown.length, 2)}`}>
            {shown.map((image, i) => (
              <button key={image.url} className="img" type="button"
                aria-label={`画像 ${i + 1} / ${post.images.length} を拡大: ${image.alt || '説明なし'}`}
                onClick={() => setDialog({ image: i })}>
                <img src={image.url} alt="" width={image.width} height={image.height} loading="lazy" />
                {i === 3 && post.images.length > 4 && (
                  <span className="more" aria-hidden="true">
                    +{post.images.length - 4}枚
                  </span>
                )}
              </button>
            ))}
          </div>
        )}
        <div className="actions">
          <Link className="act" to={`/posts/${post.id}`} aria-label={`返信 ${post.replyCount}件`}>
            <Icon name="reply" size={20} />
            <span aria-hidden="true">返信 {post.replyCount}</span>
          </Link>
          {post.mine && (
            <div className="menu-wrap" ref={menuRef}>
              <button className="act" type="button" aria-label="その他の操作" aria-expanded={menuOpen}
                onClick={() => setMenuOpen((open) => !open)}>
                <Icon name="more" size={20} />
              </button>
              {menuOpen && (
                <div className="menu" role="group" aria-label="投稿のメニュー">
                  <button type="button" autoFocus onClick={() => { setMenuOpen(false); setDialog('edit') }}>
                    編集
                  </button>
                  <button type="button" className="danger" onClick={() => { setMenuOpen(false); setDialog('delete') }}>
                    削除
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      {dialog === 'edit' && <EditDialog post={post} onClose={() => setDialog(null)} onSaved={onChange} />}
      {dialog === 'delete' && <DeleteDialog post={post} onClose={() => setDialog(null)} onDeleted={onDelete} />}
      {dialog?.image !== undefined && (
        <ImageViewer images={post.images} start={dialog.image} onClose={() => setDialog(null)} />
      )}
    </article>
  )
}

/** D-02 投稿の編集。本文だけを変えられる(画像は変えられない) */
function EditDialog({ post, onClose, onSaved }) {
  const notify = useNotice()
  const [text, setText] = useState(post.text)
  const [error, setError] = useState('')
  const left = 1000 - [...text.trim()].length

  async function save(event) {
    event.preventDefault()
    try {
      const saved = await api(`/api/posts/${post.id}`, { method: 'PUT', body: { text } })
      onSaved?.(saved)
      onClose()
      notify('投稿を編集しました')
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <Modal title="投稿を編集" onClose={onClose} initialFocus="edit-text">
      <form onSubmit={save} noValidate>
        <label className="sr-only" htmlFor="edit-text">
          投稿の本文
        </label>
        <textarea id="edit-text" value={text} onChange={(e) => setText(e.target.value)}
          aria-describedby={error ? 'edit-error' : undefined} />
        <p className={`counter${left < 0 ? ' over' : ''}`}>{left < 0 ? `${-left} 文字オーバー` : `残り ${left} 文字`}</p>
        {post.images.length > 0 && <p className="hint">画像は変更できません</p>}
        {error && (
          <p className="field-error" id="edit-error">
            {error}
          </p>
        )}
        <div className="dialog-buttons">
          <button className="btn btn-outline" type="button" onClick={onClose}>
            やめる
          </button>
          <button className="btn btn-primary" type="submit" disabled={left < 0}>
            保存する
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** D-03 投稿の削除の確認。最初は「やめる」を選んだ状態にする */
function DeleteDialog({ post, onClose, onDeleted }) {
  const notify = useNotice()

  async function remove() {
    try {
      await api(`/api/posts/${post.id}`, { method: 'DELETE' })
      onClose()
      onDeleted?.(post.id)
      notify('投稿を削除しました')
    } catch (e) {
      onClose()
      notify(e.message, { error: true })
    }
  }

  return (
    <Modal title="この投稿を削除しますか?" onClose={onClose} initialFocus="delete-cancel">
      <p>削除すると元に戻せません。</p>
      <div className="dialog-buttons">
        <button id="delete-cancel" className="btn btn-outline" type="button" onClick={onClose}>
          やめる
        </button>
        <button className="btn btn-danger" type="button" onClick={remove}>
          削除する
        </button>
      </div>
    </Modal>
  )
}

/** D-05 画像の拡大表示。←→ のボタンかキーで、同じ投稿の画像を切り替える */
function ImageViewer({ images, start, onClose }) {
  const [index, setIndex] = useState(start)
  const image = images[index]

  // 開いている間だけ、左右の矢印キーで画像を切り替える
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'ArrowLeft') setIndex((i) => Math.max(i - 1, 0))
      if (e.key === 'ArrowRight') setIndex((i) => Math.min(i + 1, images.length - 1))
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [images.length])

  return (
    <Modal title={`画像 ${index + 1} / ${images.length}`} className="viewer" onClose={onClose} initialFocus="viewer-close">
      <div className="viewer-img">
        <img src={image.url} alt={image.alt} />
      </div>
      <div className="viewer-bar">
        <button className="btn btn-outline" type="button" aria-label="前の画像" disabled={index === 0}
          onClick={() => setIndex(index - 1)}>
          ←
        </button>
        <button className="btn btn-outline" type="button" aria-label="次の画像" disabled={index === images.length - 1}
          onClick={() => setIndex(index + 1)}>
          →
        </button>
        <span className="alt">{image.alt || '(説明なし)'}</span>
        <button id="viewer-close" className="btn btn-primary" type="button" onClick={onClose}>
          閉じる
        </button>
      </div>
    </Modal>
  )
}
