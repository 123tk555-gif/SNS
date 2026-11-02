import { useParams } from 'react-router'
import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { tagName } from '../components/RichText.jsx'
import { useTimeline } from '../useTimeline.js'

/** S-12 ハッシュタグ(F-TREND-03, F-SEARCH-03)。そのタグを含む投稿(返信を含む)を新しい順に */
export default function TagPage() {
  const { name } = useParams()
  const tag = tagName(name)
  const list = useTimeline(`/api/tags/${encodeURIComponent(tag)}/posts`, { posts: true })
  return (
    <Layout title={`#${tag}`} back>
      <PostList {...list} emptyText={`#${tag} を含む投稿はありません`} onLoadMore={list.loadMore} onRetry={list.retry}
        onChange={list.replace} onDelete={list.remove} />
    </Layout>
  )
}
