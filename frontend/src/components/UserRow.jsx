import { Link } from 'react-router'
import { useAuth } from '../auth.jsx'
import Avatar from './Avatar.jsx'
import FollowButton from './FollowButton.jsx'

/**
 * ユーザーの行(画面仕様定義書 3.4)。アイコン・表示名・@ユーザーID・自己紹介(2 行まで)・フォローボタン。
 * 自分の行にはフォローボタンを出さない(F-FOLLOW-03)。
 * onChange(user): フォローの状態が変わったとき
 */
export default function UserRow({ user, onChange = () => {} }) {
  const { user: me } = useAuth()
  return (
    <div className="user-row">
      <Avatar user={user} />
      <div className="info">
        <Link className="name" to={`/users/${user.handle}`}>
          {user.displayName}
        </Link>{' '}
        <span className="handle">@{user.handle}</span>
        {user.followsMe && <span className="chip">フォローされています</span>}
        {user.bio && <p className="bio">{user.bio}</p>}
      </div>
      {user.id !== me.id && (
        <FollowButton user={user} onChange={(following) => onChange({ ...user, followedByMe: following })} />
      )}
    </div>
  )
}
