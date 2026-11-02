import { api } from '../api.js'
import { useNotice } from '../notice.jsx'

/**
 * フォローボタン(画面仕様定義書 3.4 のユーザーの行)。未フォローは「フォローする」、フォロー中は「フォロー中」。
 * フォロー中のボタンにマウスを乗せる・キーボードで選ぶと「フォローを解除」に変わる(切り替えは CSS の
 * :hover / :focus-visible で行う。押した直後もボタンが選ばれたままなので、JavaScript の focus では切り替わらないため)。
 * 押した瞬間に表示を切り替え、失敗したら元に戻してエラーを出す。
 * onChange(following, followerCount): フォローの状態が変わったとき(相手のフォロワー数を返す)
 */
export default function FollowButton({ user, onChange = () => {} }) {
  const notify = useNotice()

  async function toggle() {
    const following = !user.followedByMe
    onChange(following)
    try {
      const result = await api(`/api/users/${user.handle}/follow`, { method: following ? 'PUT' : 'DELETE' })
      onChange(result.following, result.followerCount)
      notify(following ? `@${user.handle} さんをフォローしました` : `@${user.handle} さんのフォローを解除しました`)
    } catch (e) {
      onChange(!following)
      notify(e.message, { error: true })
    }
  }

  if (!user.followedByMe) {
    return (
      <button className="btn btn-primary" type="button" onClick={toggle} aria-label={`@${user.handle} さんをフォローする`}>
        フォローする
      </button>
    )
  }
  return (
    <button className="btn btn-outline follow-on" type="button" onClick={toggle} aria-label={`@${user.handle} さんのフォローを解除`}>
      <span className="label-idle">フォロー中</span>
      <span className="label-active">フォローを解除</span>
    </button>
  )
}
