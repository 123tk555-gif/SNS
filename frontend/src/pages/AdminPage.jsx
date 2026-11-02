import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Layout from '../components/Layout.jsx'
import Modal from '../components/Modal.jsx'
import { useNotice } from '../notice.jsx'
import { fullTime } from '../time.js'
import NotFoundPage from './NotFoundPage.jsx'

const PAGE_SIZE = 50
const TABS = [
  ['posts', '投稿'],
  ['users', 'ユーザー'],
]

/** S-15 管理者画面(F-ADMIN-01〜05)。管理者以外が開くと S-99(サーバーの API も 404 を返す) */
export default function AdminPage() {
  const { user } = useAuth()
  if (user.role !== 'ADMIN') return <NotFoundPage />
  return <AdminScreen />
}

function AdminScreen() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'users' ? 'users' : 'posts'
  const q = params.get('q') ?? ''
  const page = Math.max(Number.parseInt(params.get('page') ?? '0', 10) || 0, 0)
  const [input, setInput] = useState(q)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  // 見ている条件(タブ・検索語・ページ)は URL に入れる。戻るボタンや再読み込みでも同じ表示に戻る
  const go = (next) => setParams({ tab, ...(q ? { q } : {}), ...(page ? { page } : {}), ...next }, { replace: false })

  const load = useCallback(async () => {
    setError('')
    try {
      const query = new URLSearchParams({ q, page })
      setData(await api(`/api/admin/${tab}?${query}`))
    } catch (e) {
      setError(e.message)
    }
  }, [tab, q, page])

  useEffect(() => {
    setData(null)
    setInput(q)
    load()
  }, [load, q])

  // 行の一部を、操作の結果で置き換える(表の状態の欄がすぐ変わる)
  const replaceItem = (item) => setData((d) => ({ ...d, items: d.items.map((x) => (x.id === item.id ? { ...x, ...item } : x)) }))

  const label = tab === 'users' ? 'ユーザーID・表示名・メールアドレスで検索' : '本文で検索'
  const from = data ? data.page * PAGE_SIZE : 0

  return (
    <Layout title="管理者画面">
      <nav className="tabs" aria-label="管理する対象">
        {TABS.map(([key, text]) => (
          <Link key={key} className="tab" to={`/admin?tab=${key}`} aria-current={tab === key ? 'page' : undefined}>
            {text}
          </Link>
        ))}
      </nav>
      <form className="search-form" role="search"
        onSubmit={(e) => {
          e.preventDefault()
          go({ q: input.trim() || undefined, page: undefined })
        }}>
        <label className="sr-only" htmlFor="admin-q">
          {label}
        </label>
        <input type="search" id="admin-q" value={input} placeholder={label} onChange={(e) => setInput(e.target.value)} />
        <button className="btn btn-primary" type="submit">
          検索
        </button>
      </form>
      {error ? (
        <div className="empty">
          <p className="field-error">エラー: {error}</p>
          <button className="btn btn-outline" type="button" onClick={load}>
            もう一度読み込む
          </button>
        </div>
      ) : !data ? (
        <p className="list-end" role="status">
          読み込み中…
        </p>
      ) : (
        <>
          <div className="admin-table">
            {tab === 'users' ? (
              <UserRows items={data.items} onChange={replaceItem} />
            ) : (
              <PostRows items={data.items} onChange={replaceItem} />
            )}
          </div>
          {data.items.length === 0 && <p className="empty">該当するものはありません</p>}
          <div className="pager">
            <button className="btn btn-outline" type="button" disabled={data.page === 0}
              onClick={() => go({ page: data.page - 1 || undefined })}>
              前の{PAGE_SIZE}件
            </button>
            <span role="status">
              {data.total ? `${from + 1}〜${from + data.items.length}件目(全${data.total}件)` : '0件'}
            </span>
            <button className="btn btn-outline" type="button" disabled={data.page + 1 >= data.totalPages}
              onClick={() => go({ page: data.page + 1 })}>
              次の{PAGE_SIZE}件
            </button>
          </div>
        </>
      )}
    </Layout>
  )
}

function PostRows({ items, onChange }) {
  return (
    <>
      <div className="admin-row head" aria-hidden="true">
        <span>ID</span>
        <span>投稿者</span>
        <span>本文</span>
        <span>状態</span>
        <span>操作</span>
      </div>
      {items.map((post) => (
        <PostRow key={post.id} post={post} onChange={onChange} />
      ))}
    </>
  )
}

function PostRow({ post, onChange }) {
  const [confirming, setConfirming] = useState(false)
  const notify = useNotice()
  const status = post.hidden ? '非表示' : post.authorSuspended ? '投稿者が利用停止中' : '公開中'
  const preview = [...post.text].slice(0, 80).join('')

  async function run() {
    setConfirming(false)
    try {
      onChange(await api(`/api/admin/posts/${post.id}/visibility`, { method: 'PUT', body: { hidden: !post.hidden } }))
      notify(post.hidden ? '投稿を表示に戻しました' : '投稿を非表示にしました')
    } catch (e) {
      notify(e.message, { error: true })
    }
  }

  return (
    <div className="admin-row">
      <span>{post.id}</span>
      <span>
        <Link to={`/users/${post.authorHandle}`}>@{post.authorHandle}</Link>
      </span>
      <span className="wide">
        {/* 投稿詳細は新しいタブで開く(管理の画面を離れないため) */}
        <a href={`/posts/${post.id}`} target="_blank" rel="noreferrer">
          {preview || '(画像のみ)'}
          <span className="sr-only">(新しいタブで開きます)</span>
        </a>
        <br />
        <span className="handle small">{fullTime(post.createdAt)}</span>
      </span>
      <span>
        <span className={`status${status === '公開中' ? '' : ' bad'}`}>{status}</span>
      </span>
      <span>
        {post.hidden ? (
          <button className="btn btn-outline" type="button" onClick={run}>
            表示に戻す
          </button>
        ) : (
          <button className="btn btn-danger" type="button" onClick={() => setConfirming(true)}>
            非表示にする
          </button>
        )}
      </span>
      {confirming && (
        <ConfirmDialog title="この投稿を非表示にしますか?" text="投稿者以外には表示されなくなります"
          onCancel={() => setConfirming(false)} onOk={run} />
      )}
    </div>
  )
}

function UserRows({ items, onChange }) {
  return (
    <>
      <div className="admin-row users head" aria-hidden="true">
        <span>ID</span>
        <span>ユーザー</span>
        <span>メールアドレス</span>
        <span>状態</span>
        <span>操作</span>
      </div>
      {items.map((u) => (
        <UserRow key={u.id} item={u} onChange={onChange} />
      ))}
    </>
  )
}

function UserRow({ item, onChange }) {
  const { user: me } = useAuth()
  const notify = useNotice()
  const [confirming, setConfirming] = useState(false)
  const canAct = item.id !== me.id && item.role !== 'ADMIN'
  const created = new Date(item.createdAt)

  async function run() {
    setConfirming(false)
    try {
      onChange(await api(`/api/admin/users/${item.id}/suspension`, { method: 'PUT', body: { suspended: !item.suspended } }))
      notify(item.suspended ? '利用停止を解除しました' : '利用停止にしました')
    } catch (e) {
      notify(e.message, { error: true })
    }
  }

  return (
    <div className="admin-row users">
      <span>{item.id}</span>
      <span>
        {item.handle ? <Link to={`/users/${item.handle}`}>@{item.handle}</Link> : '(初期設定前)'}
        <br />
        {item.displayName}
        {item.role === 'ADMIN' && (
          <>
            <br />
            <span className="chip">管理者</span>
          </>
        )}
      </span>
      <span className="wide">
        {item.email}
        <br />
        <span className="handle small">
          投稿 {item.postCount}件・{created.getFullYear()}年{created.getMonth() + 1}月{created.getDate()}日に登録
        </span>
      </span>
      <span>
        <span className={`status${item.suspended ? ' bad' : ''}`}>{item.suspended ? '利用停止中' : '利用中'}</span>
      </span>
      <span>
        {canAct &&
          (item.suspended ? (
            <button className="btn btn-outline" type="button" onClick={run}>
              停止を解除
            </button>
          ) : (
            <button className="btn btn-danger" type="button" onClick={() => setConfirming(true)}>
              利用停止にする
            </button>
          ))}
      </span>
      {confirming && (
        <ConfirmDialog title={`@${item.handle} さんを利用停止にしますか?`}
          text="すぐにログアウトされ、投稿も表示されなくなります" onCancel={() => setConfirming(false)} onOk={run} />
      )}
    </div>
  )
}

/** D-06 管理者の操作の確認。最初は「やめる」を選んだ状態にする */
function ConfirmDialog({ title, text, onCancel, onOk }) {
  return (
    <Modal title={title} onClose={onCancel} initialFocus="admin-cancel">
      <p>{text}</p>
      <div className="dialog-buttons">
        <button id="admin-cancel" className="btn btn-outline" type="button" onClick={onCancel}>
          やめる
        </button>
        <button className="btn btn-danger" type="button" onClick={onOk}>
          実行する
        </button>
      </div>
    </Modal>
  )
}
