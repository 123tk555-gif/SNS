import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api.js'
import AuthLayout from '../components/AuthLayout.jsx'
import Field from '../components/Field.jsx'

/** S-03 パスワード再設定(F-AUTH-04)。URL に token がなければメールの送信、あれば新しいパスワードの設定 */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  return token ? <NewPasswordForm token={token} /> : <RequestForm />
}

function RequestForm() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [sent, setSent] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!email.trim()) {
      setError('メールアドレスを入力してください')
      return
    }
    try {
      await api('/api/auth/password-reset', { method: 'POST', body: { email: email.trim() } })
      setSent(true)
    } catch (e) {
      setError(e.message)
    }
  }

  if (sent) {
    return (
      <AuthLayout title="メールを送りました">
        {/* 登録の有無で文を変えない(登録済みかどうかを知られないため) */}
        <p>入力したメールアドレスが登録されていれば、再設定のメールを送りました(リンクの有効期限は1時間です)。</p>
        <div className="auth-links">
          <Link to="/login">ログインへ</Link>
        </div>
      </AuthLayout>
    )
  }
  return (
    <AuthLayout title="パスワードの再設定">
      <p>登録したメールアドレスを入力してください。パスワードを再設定するためのリンクを送ります。</p>
      <form onSubmit={handleSubmit} noValidate>
        <Field id="reset-email" label="メールアドレス(必須)" type="email" autoComplete="email" value={email}
          error={error} onChange={(e) => setEmail(e.target.value)} />
        <button className="btn btn-primary btn-block" type="submit">
          送信する
        </button>
      </form>
      <div className="auth-links">
        <Link to="/login">ログインへ戻る</Link>
      </div>
    </AuthLayout>
  )
}

function NewPasswordForm({ token }) {
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    const length = [...password].length
    const found = {}
    if (length < 8 || length > 72) found.password = 'パスワードは8文字以上72文字以内で入力してください'
    else if (password !== confirmation) found.confirmation = 'パスワードが一致しません'
    setErrors(found)
    if (found.password || found.confirmation) return
    try {
      await api('/api/auth/password-reset/confirm', { method: 'POST', body: { token, password } })
      navigate('/login', { replace: true, state: { notice: 'パスワードを変更しました。新しいパスワードでログインしてください' } })
    } catch (e) {
      setFormError(e.message)
    }
  }

  return (
    <AuthLayout title="新しいパスワードの設定">
      <form onSubmit={handleSubmit} noValidate>
        {formError && (
          <p className="field-error" role="alert">
            エラー: {formError}
          </p>
        )}
        <Field id="new-password" label="新しいパスワード(必須)" type="password" autoComplete="new-password"
          hint="8文字以上72文字以内" value={password} error={errors.password}
          onChange={(e) => setPassword(e.target.value)} />
        <Field id="new-password-confirm" label="確認のためもう一度(必須)" type="password" autoComplete="new-password"
          value={confirmation} error={errors.confirmation} onChange={(e) => setConfirmation(e.target.value)} />
        <button className="btn btn-primary btn-block" type="submit">
          設定する
        </button>
      </form>
      {formError && (
        <div className="auth-links">
          <Link to="/reset-password">再設定のメールを送り直す</Link>
        </div>
      )}
    </AuthLayout>
  )
}
