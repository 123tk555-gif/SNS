import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthLayout from '../components/AuthLayout.jsx'

/** S-02a メールアドレスの確認。メールのリンクから開き、開いたらすぐに確認する */
export default function VerifyEmailPage() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [error, setError] = useState('')
  // リンクの値は 1 回しか使えないので、開発中の StrictMode で 2 回呼ばれても 1 回だけ送る
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    const token = searchParams.get('token')
    if (!token) {
      setError('リンクが正しくありません')
      return
    }
    api('/api/auth/verify-email', { method: 'POST', body: { token } })
      .then((user) => {
        setUser(user)
        navigate(user.onboarded ? '/' : '/onboarding', { replace: true })
      })
      .catch((e) => setError(e.message))
  }, [searchParams, setUser, navigate])

  if (error) {
    return (
      <AuthLayout title="確認できませんでした">
        <p className="field-error" role="alert">
          エラー: {error}
        </p>
        <p>ログイン画面から、確認メールを送り直せます。</p>
        <Link className="btn btn-primary btn-block" to="/login">
          ログイン画面へ
        </Link>
      </AuthLayout>
    )
  }
  return (
    <AuthLayout title="メールアドレスの確認">
      <p role="status">確認しています…</p>
    </AuthLayout>
  )
}
