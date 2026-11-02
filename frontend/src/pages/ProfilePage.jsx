import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { api } from '../api.js'
import FollowButton from '../components/FollowButton.jsx'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import RichText from '../components/RichText.jsx'
import { useTimeline } from '../useTimeline.js'

const COLORS = ['#1d4ed8', '#b4410c', '#047857', '#6d28d9', '#be185d', '#0f766e', '#a21caf', '#9a3412']
const TABS = [
  ['posts', '投稿'],
  ['replies', '返信を含む'],
  ['media', '画像'],
]

/**
 * S-07 プロフィール(F-PROF-01, 03, 04, F-FOLLOW-01)。
 * プロフィールの編集は設定画面(M8)、ミュート・ブロックは M8 で加える。
 */
export default function ProfilePage() {
  const { handle } = useParams()
  const [searchParams] = useSearchParams()
  const tab = TABS.some(([key]) => key === searchParams.get('tab')) ? searchParams.get('tab') : 'posts'
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      setProfile(await api(`/api/users/${encodeURIComponent(handle)}`))
    } catch (e) {
      setError(e.status === 404 ? { notFound: true } : { message: e.message })
    }
  }, [handle])

  useEffect(() => {
    setProfile(null)
    load()
  }, [load])

  if (error?.notFound) {
    return (
      <Layout title="ページが見つかりません" back>
        <div className="empty">
          <p>このユーザーは見つかりませんでした。</p>
          <Link className="btn btn-primary" to="/">
            ホームへ戻る
          </Link>
        </div>
      </Layout>
    )
  }
  if (!profile) {
    return (
      <Layout title="プロフィール" back>
        {error ? (
          <div className="empty">
            <p className="field-error">エラー: {error.message}</p>
            <button className="btn btn-outline" type="button" onClick={load}>
              もう一度読み込む
            </button>
          </div>
        ) : (
          <p className="list-end" role="status">
            読み込み中…
          </p>
        )}
      </Layout>
    )
  }

  const color = COLORS[profile.id % COLORS.length]
  const joined = new Date(profile.joinedAt)

  return (
    <Layout title={profile.displayName} sub={`${profile.postCount}件の投稿`} back={!profile.me}>
      <div className="band" style={{ background: profile.headerUrl ? `url("${profile.headerUrl}") center / cover` : `${color}33` }} />
      <section className="profile" aria-label="プロフィール">
        <div className="profile-top">
          <span className="avatar lg" aria-hidden="true"
            style={{ background: profile.avatarUrl ? `${color} url("${profile.avatarUrl}") center / cover` : color }}>
            {profile.avatarUrl ? '' : [...profile.displayName][0]}
          </span>
          <div className="buttons">
            {profile.me ? (
              <>
                <Link className="btn btn-outline" to="/settings">
                  プロフィールを編集
                </Link>
              </>
            ) : (
              <FollowButton user={profile}
                onChange={(following, followerCount) => setProfile((p) => ({
                  ...p,
                  followedByMe: following,
                  followerCount: followerCount ?? p.followerCount + (following ? 1 : -1),
                }))} />
            )}
          </div>
        </div>
        <h2>{profile.displayName}</h2>
        <div>
          <span className="handle">@{profile.handle}</span>
          {profile.followsMe && <span className="chip">フォローされています</span>}
        </div>
        {profile.bio && (
          <p className="bio">
            <RichText text={profile.bio} />
          </p>
        )}
        <p className="joined">
          {joined.getFullYear()}年{joined.getMonth() + 1}月から利用しています
        </p>
        <div className="counts">
          <Link to={`/users/${profile.handle}/following`}>
            <strong>{profile.followingCount}</strong>フォロー
          </Link>
          <Link to={`/users/${profile.handle}/followers`}>
            <strong>{profile.followerCount}</strong>フォロワー
          </Link>
        </div>
        {profile.me && (
          // スマートフォンでは下のメニューに出ない画面へのリンク(画面仕様定義書 3.2)
          <p className="mobile-links">
            <Link to="/bookmarks">ブックマーク</Link>
            <Link to="/settings">設定</Link>
          </p>
        )}
      </section>
      <nav className="tabs" aria-label="投稿の種類">
        {TABS.map(([key, label]) => (
          <Link key={key} className="tab" to={`/users/${profile.handle}?tab=${key}`}
            aria-current={tab === key ? 'page' : undefined}>
            {label}
          </Link>
        ))}
      </nav>
      <AuthorPosts handle={profile.handle} tab={tab} />
    </Layout>
  )
}

function AuthorPosts({ handle, tab }) {
  const list = useTimeline(`/api/users/${encodeURIComponent(handle)}/posts?tab=${tab}`)
  return (
    <PostList {...list} emptyText="まだ投稿がありません" onLoadMore={list.loadMore} onRetry={list.retry}
      onChange={list.replace} onDelete={list.remove} />
  )
}
