import { useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthLayout from '../components/AuthLayout.jsx'
import Field from '../components/Field.jsx'

// サーバーが返す文(AuthService.NOT_VERIFIED)。この文のときだけ「確認メールを送り直す」ボタンを出す
const NOT_VERIFIED = 'メールアドレスの確認が済んでいません'

// Google ログインから戻ったときの URL の ?error=
const URL_ERRORS = {
  suspended: 'このアカウントは利用停止中です',
  google: 'Google でログインできませんでした。もう一度お試しください',
}

/** S-01 ログイン(F-AUTH-02, 03, 06, 07) */
export default function LoginPage() {
  const { googleEnabled, setUser } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(URL_ERRORS[searchParams.get('error')] ?? '')
  const [unverified, setUnverified] = useState(false)
  const [resendMessage, setResendMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const errorRef = useRef(null)

  async function handleSubmit(event) {
    event.preventDefault()
    setUnverified(false)
    setResendMessage('')
    if (!email.trim() || !password) {
      setError('メールアドレスとパスワードを入力してください')
      return
    }
    setSubmitting(true)
    try {
      const user = await api('/api/auth/login', { method: 'POST', body: { email, password } })
      setUser(user)
      const from = location.state?.from
      navigate(user.onboarded ? from || '/' : '/onboarding', { replace: true })
    } catch (e) {
      setError(e.message)
      setUnverified(e.status === 403 && e.message === NOT_VERIFIED)
      setSubmitting(false)
      errorRef.current?.focus()
    }
  }

  async function resend() {
    try {
      await api('/api/auth/verify-email/resend', { method: 'POST', body: { email } })
      setResendMessage('確認メールを送りました')
    } catch (e) {
      setResendMessage(e.message)
    }
  }

  return (
    <AuthLayout title="ログイン" notice={location.state?.notice}>
      {googleEnabled && (
        <>
          <a className="btn btn-outline btn-block" href="/oauth2/authorization/google">
            Google でログイン
          </a>
          <div className="divider">または</div>
        </>
      )}
      <form onSubmit={handleSubmit} noValidate>
        {error && (
          <p className="field-error" role="alert" tabIndex={-1} ref={errorRef}>
            エラー: {error}
          </p>
        )}
        {unverified && (
          <p>
            <button className="btn btn-outline" type="button" onClick={resend}>
              確認メールを送り直す
            </button>{' '}
            {resendMessage && <span role="status">{resendMessage}</span>}
          </p>
        )}
        <Field id="login-email" label="メールアドレス(必須)" type="email" autoComplete="email" value={email}
          onChange={(e) => setEmail(e.target.value)} />
        <Field id="login-password" label="パスワード(必須)" type="password" autoComplete="current-password"
          value={password} onChange={(e) => setPassword(e.target.value)} />
        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? '送信中…' : 'ログイン'}
        </button>
      </form>
      <div className="auth-links">
        <Link to="/reset-password">パスワードを忘れた方</Link>
        <Link to="/signup">アカウントを作成する</Link>
      </div>
    </AuthLayout>
  )
}
