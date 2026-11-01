import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthLayout from '../components/AuthLayout.jsx'
import Field from '../components/Field.jsx'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(email, password) {
  const errors = {}
  if (!EMAIL_PATTERN.test(email.trim())) errors.email = 'メールアドレスの形で入力してください'
  const length = [...password].length
  if (length < 8 || length > 72) errors.password = 'パスワードは8文字以上72文字以内で入力してください'
  return errors
}

/** S-02 新規登録(F-AUTH-01, 02) */
export default function SignupPage() {
  const { googleEnabled } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [sent, setSent] = useState(false)
  const [resendMessage, setResendMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    const found = validate(email, password)
    setErrors(found)
    if (found.email || found.password) {
      document.getElementById(found.email ? 'signup-email' : 'signup-password')?.focus()
      return
    }
    setSubmitting(true)
    try {
      await api('/api/auth/signup', { method: 'POST', body: { email: email.trim(), password } })
      setSent(true)
    } catch (e) {
      if (e.status === 409) setErrors({ email: e.message })
      else setErrors({ email: e.fieldError('email'), password: e.fieldError('password') })
      if (e.status !== 409 && !e.errors.length) setFormError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function resend() {
    try {
      await api('/api/auth/verify-email/resend', { method: 'POST', body: { email: email.trim() } })
      setResendMessage('確認メールを送り直しました')
    } catch (e) {
      setResendMessage(e.message)
    }
  }

  if (sent) {
    return (
      <AuthLayout title="確認メールを送りました">
        <p>メールのリンクを開いて、登録を完了してください(リンクの有効期限は24時間です)。</p>
        <p className="hint">送り先: {email.trim()}</p>
        <button className="btn btn-outline btn-block" type="button" onClick={resend}>
          確認メールを送り直す
        </button>
        {resendMessage && <p role="status">{resendMessage}</p>}
        <div className="auth-links">
          <Link to="/login">ログインへ</Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="アカウントを作成">
      {googleEnabled && (
        <>
          <a className="btn btn-outline btn-block" href="/oauth2/authorization/google">
            Google で登録
          </a>
          <div className="divider">または</div>
        </>
      )}
      <form onSubmit={handleSubmit} noValidate>
        {formError && (
          <p className="field-error" role="alert">
            エラー: {formError}
          </p>
        )}
        <Field id="signup-email" label="メールアドレス(必須)" type="email" autoComplete="email" value={email}
          error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        {errors.email === 'このメールアドレスは登録済みです' && (
          <p>
            <Link to="/login">ログイン画面へ</Link>
          </p>
        )}
        <Field id="signup-password" label="パスワード(必須)" type="password" autoComplete="new-password"
          hint="8文字以上72文字以内" value={password} error={errors.password}
          onChange={(e) => setPassword(e.target.value)} />
        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? '送信中…' : '登録する'}
        </button>
      </form>
      <div className="auth-links">
        <Link to="/login">アカウントをお持ちの方はログイン</Link>
      </div>
    </AuthLayout>
  )
}
