import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api.js'
import InfiniteList from '../components/InfiniteList.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import TrendList from '../components/TrendList.jsx'
import UserRow from '../components/UserRow.jsx'
import { useTimeline } from '../useTimeline.js'

const TABS = [
  ['posts', '投稿'],
  ['users', 'ユーザー'],
  ['tags', 'ハッシュタグ'],
]
const MAX_QUERY = 100

/** 言葉が @ で始まればユーザー、# で始まればハッシュタグのタブを開く(画面仕様定義書 S-10) */
function tabFor(query, current = 'posts') {
  if (/^[@＠]/.test(query)) return 'users'
  if (/^[#＃]/.test(query)) return 'tags'
  return current
}

/**
 * S-10 検索(F-SEARCH-01〜05)。検索の言葉とタブは URL に入れ、ブラウザの「戻る」で前の検索結果に戻れるようにする。
 * 検索する前は、トレンドの上位 5 件を出す
 */
export default function SearchPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const q = (searchParams.get('q') ?? '').trim()
  const tabParam = searchParams.get('tab')
  const tab = TABS.some(([key]) => key === tabParam) ? tabParam : tabFor(q)
  const [text, setText] = useState(q)
  const [error, setError] = useState('')

  // ブラウザの「戻る」などで URL が変わったら、検索欄も合わせる
  useEffect(() => setText(q), [q])

  function submit(event) {
    event.preventDefault()
    const query = text.trim()
    if ([...query].length > MAX_QUERY) {
      setError('100文字以内で入力してください')
      return
    }
    setError('')
    if (!query) return
    navigate(`/search?q=${encodeURIComponent(query)}&tab=${tabFor(query, tab)}`)
  }

  return (
    <Layout title="検索">
      <form className="search-form" role="search" onSubmit={submit} noValidate>
        <label className="sr-only" htmlFor="search-q">
          検索する言葉
        </label>
        <input type="search" id="search-q" value={text} placeholder="キーワード・@ユーザーID・#タグで検索"
          aria-describedby={error ? 'search-error' : undefined} onChange={(e) => setText(e.target.value)} />
        <button className="btn btn-primary" type="submit">
          検索
        </button>
      </form>
      {error && (
        <p className="field-error" id="search-error" style={{ padding: '0 16px' }}>
          {error}
        </p>
      )}
      <nav className="tabs" aria-label="検索の種類">
        {TABS.map(([key, label]) => (
          <Link key={key} className="tab" to={`/search?q=${encodeURIComponent(q)}&tab=${key}`}
            aria-current={tab === key ? 'page' : undefined}>
            {label}
          </Link>
        ))}
      </nav>
      {!q ? (
        <BeforeSearch />
      ) : tab === 'users' ? (
        <UserResults key={q} query={q} />
      ) : tab === 'tags' ? (
        <TagResults key={q} query={q} />
      ) : (
        <PostResults key={q} query={q} />
      )}
    </Layout>
  )
}

const noResult = (q) => `『${q}』に一致する結果はありません`

function PostResults({ query }) {
  const list = useTimeline(`/api/search/posts?q=${encodeURIComponent(query)}`, { posts: true })
  return (
    <PostList {...list} emptyText={noResult(query)} onLoadMore={list.loadMore} onRetry={list.retry}
      onChange={list.replace} onDelete={list.remove} />
  )
}

function UserResults({ query }) {
  const list = useTimeline(`/api/search/users?q=${encodeURIComponent(query)}`)
  return (
    <InfiniteList {...list} emptyText={noResult(query)} onLoadMore={list.loadMore} onRetry={list.retry}
      renderItem={(user) => <UserRow key={user.id} user={user} onChange={list.replace} />} />
  )
}

function TagResults({ query }) {
  const [tags, setTags] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    api(`/api/search/tags?q=${encodeURIComponent(query)}`).then((r) => setTags(r.items)).catch((e) => setError(e.message))
  }, [query])
  if (error) return <p className="field-error" style={{ padding: '16px' }}>エラー: {error}</p>
  if (!tags) return <p className="list-end" role="status">読み込み中…</p>
  return <TrendList tags={tags} emptyText={noResult(query)} />
}

/** 検索する前: トレンドの上位 5 件と、トレンドの画面へのリンク(スマートフォンではメニューにないため) */
function BeforeSearch() {
  const [tags, setTags] = useState(null)
  useEffect(() => {
    api('/api/trends').then((r) => setTags(r.tags.slice(0, 5))).catch(() => setTags([]))
  }, [])
  return (
    <>
      <h2 className="section-title">いまのトレンド</h2>
      {tags ? <TrendList tags={tags} ranked /> : <p className="list-end" role="status">読み込み中…</p>}
      <p style={{ padding: '0 16px' }}>
        <Link to="/trends">トレンドをすべて見る</Link>
      </p>
    </>
  )
}
