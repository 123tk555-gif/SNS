import { useLocation } from 'react-router'
import { useAuth } from '../auth.jsx'
import AuthLayout from '../components/AuthLayout.jsx'

/** S-05 ホーム(仮)。タイムラインは M3 で作る。今はログインできたことの確認とログアウトだけ */
export default function HomePage() {
  const { user, logout } = useAuth()
  const location = useLocation()

  return (
    <AuthLayout title="ホーム" notice={location.state?.notice}>
      <p>
        ようこそ、<strong>{user.displayName}</strong> さん(@{user.handle})
        {user.role === 'ADMIN' && <span className="chip">管理者</span>}
      </p>
      <p className="hint">タイムラインは次の段階(M3)で作ります。</p>
      <button className="btn btn-outline btn-block" type="button" onClick={logout}>
        ログアウト
      </button>
    </AuthLayout>
  )
}
