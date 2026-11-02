import { useAuth } from '../auth.jsx'
import Layout from '../components/Layout.jsx'

/** まだ作っていない画面。どのマイルストーンで作るかを表示する(設定画面には、仮にログアウトのボタンを置く) */
export default function ComingSoonPage({ title, milestone, showLogout = false }) {
  const { logout } = useAuth()
  return (
    <Layout title={title}>
      <div className="empty">
        <p>この画面は {milestone} で作ります。</p>
        {showLogout && (
          <button className="btn btn-outline" type="button" onClick={logout}>
            ログアウト
          </button>
        )}
      </div>
    </Layout>
  )
}
