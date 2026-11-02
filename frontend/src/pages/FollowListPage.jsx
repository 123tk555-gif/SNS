import { Link, useParams } from 'react-router'
import InfiniteList from '../components/InfiniteList.jsx'
import Layout from '../components/Layout.jsx'
import UserRow from '../components/UserRow.jsx'
import { useTimeline } from '../useTimeline.js'

/** S-08 フォロー中・フォロワーの一覧(F-FOLLOW-02)。kind: following / followers */
export default function FollowListPage({ kind }) {
  const { handle } = useParams()
  const list = useTimeline(`/api/users/${encodeURIComponent(handle)}/${kind}`)
  const empty = kind === 'following' ? 'まだフォローしていません' : 'まだフォロワーはいません'

  return (
    <Layout title={kind === 'following' ? 'フォロー中' : 'フォロワー'} sub={`@${handle}`} back>
      <nav className="tabs" aria-label="一覧の種類">
        <Link className="tab" to={`/users/${handle}/following`} aria-current={kind === 'following' ? 'page' : undefined}>
          フォロー中
        </Link>
        <Link className="tab" to={`/users/${handle}/followers`} aria-current={kind === 'followers' ? 'page' : undefined}>
          フォロワー
        </Link>
      </nav>
      <InfiniteList {...list} emptyText={empty} onLoadMore={list.loadMore} onRetry={list.retry}
        renderItem={(user) => <UserRow key={user.id} user={user} onChange={list.replace} />} />
    </Layout>
  )
}
