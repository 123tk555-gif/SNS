import InfiniteList from './InfiniteList.jsx'
import PostCard from './PostCard.jsx'

/** 投稿の一覧。続きの読み込みと状態の表示は InfiniteList が行う */
export default function PostList({ onChange, onDelete, ...list }) {
  return (
    <InfiniteList {...list}
      renderItem={(post) => <PostCard key={post.id} post={post} onChange={onChange} onDelete={onDelete} />} />
  )
}
