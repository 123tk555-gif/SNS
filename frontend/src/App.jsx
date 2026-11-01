import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth.jsx'
import AuthLayout from './components/AuthLayout.jsx'
import HomePage from './pages/HomePage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import OnboardingPage from './pages/OnboardingPage.jsx'
import ResetPasswordPage from './pages/ResetPasswordPage.jsx'
import SignupPage from './pages/SignupPage.jsx'
import VerifyEmailPage from './pages/VerifyEmailPage.jsx'

/** ログイン状態を確かめている間・確かめられなかったときの表示 */
function AuthGate({ children }) {
  const { status, error, retry } = useAuth()
  if (status === 'loading') {
    return (
      <AuthLayout title="読み込み中">
        <p role="status">読み込み中…</p>
      </AuthLayout>
    )
  }
  if (status === 'error') {
    return (
      <AuthLayout title="接続できませんでした">
        <p className="field-error">{error}</p>
        <button className="btn btn-primary btn-block" type="button" onClick={retry}>
          もう一度読み込む
        </button>
      </AuthLayout>
    )
  }
  return children
}

/** ログインが必要な画面。未ログインならログイン画面へ、初期設定前なら初期設定画面へ移す(F-AUTH-06) */
function RequireAuth({ children, allowBeforeOnboarding = false }) {
  const { status, user, loggedOut } = useAuth()
  const location = useLocation()
  if (status !== 'authenticated') {
    const state = loggedOut ? { notice: 'ログアウトしました' } : { from: location.pathname + location.search }
    return <Navigate to="/login" replace state={state} />
  }
  if (!user.onboarded && !allowBeforeOnboarding) return <Navigate to="/onboarding" replace />
  return children
}

/** ログインしていない人向けの画面(ログイン・新規登録)。ログイン済みならホームへ */
function PublicOnly({ children }) {
  const { status, user } = useAuth()
  if (status === 'authenticated') return <Navigate to={user.onboarded ? '/' : '/onboarding'} replace />
  return children
}

export default function App() {
  return (
    <AuthGate>
      <Routes>
        <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
        <Route path="/signup" element={<PublicOnly><SignupPage /></PublicOnly>} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/onboarding" element={<RequireAuth allowBeforeOnboarding><OnboardingPage /></RequireAuth>} />
        <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthGate>
  )
}
