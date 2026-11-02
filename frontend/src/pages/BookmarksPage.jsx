import Layout from '../components/Layout.jsx'
import PostList from '../components/PostList.jsx'
import { useTimeline } from '../useTimeline.js'

/**
 * S-13 ブックマーク(F-REACT-05)。ブックマークした新しい順。
 * 解除しても、画面を開き直すまでは一覧から消さない(押し間違えたときに戻せるように)。
 */
export default function BookmarksPage() {
  const bookmarks = useTimeline('/api/bookmarks', { posts: true })
  return (
    <Layout title="ブックマーク" sub="あなただけに表示されます">
      <PostList {...bookmarks}
        emptyText="ブックマークした投稿はここに表示されます。投稿のしおりのボタンで保存できます"
        onLoadMore={bookmarks.loadMore} onRetry={bookmarks.retry} onChange={bookmarks.replace}
        onDelete={bookmarks.remove} />
    </Layout>
  )
}
