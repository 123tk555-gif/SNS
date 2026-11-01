import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import AuthLayout from '../components/AuthLayout.jsx'
import Field from '../components/Field.jsx'

const HANDLE_PATTERN = /^[A-Za-z0-9_]{3,15}$/

/** S-04 初期設定(F-AUTH-05)。ユーザーIDは入力を止めて 0.5 秒たったら、使えるかを確かめる */
export default function OnboardingPage() {
  const { user, setUser, logout } = useAuth()
  const navigate = useNavigate()
  const [handle, setHandle] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [availability, setAvailability] = useState(null)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!handle) {
      setAvailability(null)
      return undefined
    }
    if (!HANDLE_PATTERN.test(handle)) {
      setAvailability({ available: false, message: 'ユーザーIDは半角英数字と_の3〜15文字で入力してください' })
      return undefined
    }
    const timer = setTimeout(() => {
      api(`/api/handles/${encodeURIComponent(handle)}/availability`)
        .then(setAvailability)
        .catch(() => setAvailability(null))
    }, 500)
    return () => clearTimeout(timer)
  }, [handle])

  if (user.onboarded) return <Navigate to="/" replace />

  async function handleSubmit(event) {
    event.preventDefault()
    const found = {}
    if (!HANDLE_PATTERN.test(handle)) found.handle = 'ユーザーIDは半角英数字と_の3〜15文字で入力してください'
    const nameLength = [...displayName.trim()].length
    if (nameLength < 1 || nameLength > 50) found.displayName = '表示名は1〜50文字で入力してください'
    setErrors(found)
    if (found.handle || found.displayName) {
      document.getElementById(found.handle ? 'onb-handle' : 'onb-name')?.focus()
      return
    }
    setSubmitting(true)
    try {
      const updated = await api('/api/me/onboarding', { method: 'POST', body: { handle, displayName } })
      setUser(updated)
      navigate('/', { replace: true, state: { notice: 'ようこそ!' } })
    } catch (e) {
      // 入力チェックのエラーは項目ごと、それ以外(ユーザーIDの重複・予約語など)はユーザーIDの欄に出す
      if (e.errors.length) setErrors({ handle: e.fieldError('handle'), displayName: e.fieldError('displayName') })
      else setErrors({ handle: e.message })
      setSubmitting(false)
    }
  }

  const handleStatus = errors.handle ?? (availability && !availability.available ? availability.message : undefined)

  return (
    <AuthLayout title="プロフィールを設定しましょう">
      <form onSubmit={handleSubmit} noValidate>
        <div className="handle-input">
          <span aria-hidden="true">@</span>
          <div style={{ flex: 1 }}>
            <Field id="onb-handle" label="ユーザーID(必須)" hint="半角英数字と_の3〜15文字。あとから変更できません"
              autoComplete="off" value={handle} error={handleStatus}
              onChange={(e) => {
                setHandle(e.target.value.trim())
                setErrors((x) => ({ ...x, handle: undefined }))
              }} />
          </div>
        </div>
        {availability?.available && !errors.handle && (
          <p className="field-ok" role="status">
            使えます
          </p>
        )}
        <Field id="onb-name" label="表示名(必須)" hint={`残り ${50 - [...displayName].length} 文字`} value={displayName}
          error={errors.displayName} onChange={(e) => setDisplayName(e.target.value)} />
        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? '送信中…' : 'はじめる'}
        </button>
      </form>
      <div className="auth-links">
        <button className="btn btn-outline" type="button" onClick={logout}>
          ログアウト
        </button>
      </div>
    </AuthLayout>
  )
}
