import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Avatar from '../components/Avatar.jsx'
import Layout from '../components/Layout.jsx'
import { IMAGE_TYPES, prepareImage } from '../images.js'
import { useNotice } from '../notice.jsx'
import { relationMessage, setRelation } from '../relations.js'
import { loadTheme, saveTheme, THEMES } from '../theme.js'

const COLORS = ['#1d4ed8', '#b4410c', '#047857', '#6d28d9', '#be185d', '#0f766e', '#a21caf', '#9a3412']
const MAX_NAME = 50
const MAX_BIO = 160

/**
 * S-14 設定(F-PROF-02, F-BLOCK-03, F-AUTH-03)。
 * 1 画面を見出しで「プロフィール」「表示」「ミュート・ブロック」「アカウント」に分ける。
 */
export default function SettingsPage() {
  const { user, logout } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      setProfile(await api(`/api/users/${encodeURIComponent(user.handle)}`))
    } catch (e) {
      setError(e.message)
    }
  }, [user.handle])

  useEffect(() => {
    load()
  }, [load])

  return (
    <Layout title="設定">
      <section className="settings-section" aria-labelledby="settings-profile">
        <h2 id="settings-profile">プロフィール</h2>
        {user.guest ? (
          <p>お試しのアカウントは、みんなで使うため、プロフィールを変えられません。自分のアカウントを作ると、表示名・自己紹介・アイコンを変えられます</p>
        ) : profile ? (
          <ProfileForm profile={profile} onSaved={setProfile} />
        ) : error ? (
          <div>
            <p className="field-error">エラー: {error}</p>
            <button className="btn btn-outline" type="button" onClick={load}>
              もう一度読み込む
            </button>
          </div>
        ) : (
          <p role="status">読み込み中…</p>
        )}
      </section>
      <section className="settings-section" aria-labelledby="settings-display">
        <h2 id="settings-display">表示</h2>
        <ThemeSelect />
      </section>
      <section className="settings-section" aria-labelledby="settings-mutes">
        <h2 id="settings-mutes">ミュート中のユーザー</h2>
        <RelationList kind="mute" emptyText="ミュートしているユーザーはいません" />
      </section>
      <section className="settings-section" aria-labelledby="settings-blocks">
        <h2 id="settings-blocks">ブロック中のユーザー</h2>
        <RelationList kind="block" emptyText="ブロックしているユーザーはいません" />
      </section>
      <section className="settings-section" aria-labelledby="settings-account">
        <h2 id="settings-account">アカウント</h2>
        <p>{user.guest ? 'お試しのアカウントでログインしています' : `メールアドレス: ${user.email}`}</p>
        <button className="btn btn-outline" type="button" onClick={logout}>
          ログアウト
        </button>
      </section>
    </Layout>
  )
}

/** アイコン画像・ヘッダー画像(選ぶとすぐに保存する)と、表示名・自己紹介(「保存する」で保存) */
function ProfileForm({ profile, onSaved }) {
  const { refresh } = useAuth()
  const notify = useNotice()
  const [displayName, setDisplayName] = useState(profile.displayName)
  const [bio, setBio] = useState(profile.bio ?? '')
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const nameLength = [...displayName.trim()].length
  const bioLeft = MAX_BIO - [...bio.trim()].length

  // 保存したら、画面の左のメニュー・投稿欄のアイコンなど(ログイン中の利用者の情報)も新しくする
  const saved = useCallback(async (next, message) => {
    onSaved(next)
    await refresh()
    notify(message)
  }, [onSaved, refresh, notify])

  async function submit(event) {
    event.preventDefault()
    const found = {}
    if (nameLength === 0) found.displayName = '表示名を入力してください'
    else if (nameLength > MAX_NAME) found.displayName = `表示名は${MAX_NAME}文字以内で入力してください`
    if (bioLeft < 0) found.bio = `自己紹介は${MAX_BIO}文字以内で入力してください`
    setErrors(found)
    if (Object.keys(found).length > 0) {
      document.getElementById(found.displayName ? 'profile-name' : 'profile-bio')?.focus()
      return
    }
    setSaving(true)
    try {
      const next = await api('/api/me/profile', { method: 'PUT', body: { displayName, bio } })
      setDisplayName(next.displayName)
      setBio(next.bio ?? '')
      await saved(next, 'プロフィールを保存しました')
    } catch (e) {
      setErrors({ form: e.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <ImageField kind="avatar" label="アイコン画像" profile={profile} onSaved={saved} />
      <ImageField kind="header" label="ヘッダー画像" profile={profile} onSaved={saved} />
      <div className="field">
        <label htmlFor="profile-name">表示名(必須)</label>
        <input id="profile-name" type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)}
          aria-invalid={errors.displayName ? 'true' : undefined}
          aria-describedby={errors.displayName ? 'profile-name-hint profile-name-error' : 'profile-name-hint'} />
        <p className="hint" id="profile-name-hint">
          {MAX_NAME}文字まで(今 {nameLength} 文字)
        </p>
        {errors.displayName && (
          <p className="field-error" id="profile-name-error">
            {errors.displayName}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="profile-bio">自己紹介</label>
        <textarea id="profile-bio" value={bio} onChange={(e) => setBio(e.target.value)}
          aria-invalid={errors.bio ? 'true' : undefined}
          aria-describedby={errors.bio ? 'profile-bio-hint profile-bio-error' : 'profile-bio-hint'} />
        <p className={`counter${bioLeft < 0 ? ' over' : ''}`} id="profile-bio-hint">
          {bioLeft < 0 ? `${-bioLeft} 文字オーバー` : `残り ${bioLeft} 文字`}
        </p>
        {errors.bio && (
          <p className="field-error" id="profile-bio-error">
            {errors.bio}
          </p>
        )}
      </div>
      {errors.form && <p className="field-error">{errors.form}</p>}
      <button className="btn btn-primary" type="submit" disabled={saving}>
        {saving ? '保存中…' : '保存する'}
      </button>
    </form>
  )
}

/** 画像の欄。今の画像(アイコンは丸く切り抜く)と「画像を選ぶ」「削除」 */
function ImageField({ kind, label, profile, onSaved }) {
  const notify = useNotice()
  const input = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const url = kind === 'avatar' ? profile.avatarUrl : profile.headerUrl
  const color = COLORS[profile.id % COLORS.length]
  const id = `${kind}-file`

  async function change(file) {
    setError('')
    setBusy(true)
    try {
      let next
      if (file) {
        const form = new FormData()
        form.append('file', await prepareImage(file))
        next = await api(`/api/me/${kind}`, { method: 'PUT', form })
      } else {
        next = await api(`/api/me/${kind}`, { method: 'DELETE' })
      }
      await onSaved(next, `${label}を${file ? '変更' : '削除'}しました`)
    } catch (e) {
      setError(e.message)
      notify(e.message, { error: true })
    } finally {
      setBusy(false)
      if (input.current) input.current.value = '' // 同じファイルをもう一度選べるように
    }
  }

  return (
    <div className="field">
      <span className="label" id={`${kind}-label`}>
        {label}
      </span>
      <div className="image-field">
        {kind === 'avatar' ? (
          <span className="avatar lg" aria-hidden="true"
            style={{ background: url ? `${color} url("${url}") center / cover` : color, border: 0 }}>
            {url ? '' : [...profile.displayName][0]}
          </span>
        ) : (
          <span className="header-preview" aria-hidden="true"
            style={{ background: url ? `url("${url}") center / cover` : `${color}33` }} />
        )}
        <input ref={input} id={id} className="sr-only" type="file" tabIndex={-1} accept={IMAGE_TYPES.join(',')}
          aria-labelledby={`${kind}-label`} onChange={(e) => e.target.files[0] && change(e.target.files[0])} />
        <button className="btn btn-outline" type="button" disabled={busy} aria-describedby={`${kind}-label`}
          onClick={() => input.current.click()}>
          {busy ? '保存中…' : '画像を選ぶ'}
        </button>
        {url && (
          <button className="btn btn-outline" type="button" disabled={busy} aria-describedby={`${kind}-label`}
            onClick={() => change(null)}>
            削除
          </button>
        )}
      </div>
      {error && <p className="field-error">{error}</p>}
    </div>
  )
}

/** 表示モード。選ぶとすぐに切り替わり、ブラウザに保存する */
function ThemeSelect() {
  const [theme, setTheme] = useState(loadTheme)
  return (
    <fieldset className="plain-fieldset">
      <legend className="label">表示モード</legend>
      <div className="radio-row">
        {THEMES.map(([value, label]) => (
          <label key={value}>
            <input type="radio" name="theme" value={value} checked={theme === value}
              onChange={() => {
                setTheme(value)
                saveTheme(value)
              }} />
            {label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

/**
 * ミュート中・ブロック中のユーザーの一覧(F-BLOCK-03)。
 * 解除してもその場では一覧から消さず「解除しました」と出し、ボタンを「もう一度…」に変える(押し間違えたときに戻せるように)
 */
function RelationList({ kind, emptyText }) {
  const [users, setUsers] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const page = await api(kind === 'mute' ? '/api/me/mutes' : '/api/me/blocks')
      setUsers(page.items.map((u) => ({ ...u, on: true })))
    } catch (e) {
      setError(e.message)
    }
  }, [kind])

  useEffect(() => {
    load()
  }, [load])

  if (error) {
    return (
      <div>
        <p className="field-error">エラー: {error}</p>
        <button className="btn btn-outline" type="button" onClick={load}>
          もう一度読み込む
        </button>
      </div>
    )
  }
  if (!users) return <p role="status">読み込み中…</p>
  if (users.length === 0) return <p>{emptyText}</p>
  return users.map((u) => (
    <RelationRow key={u.id} user={u} kind={kind}
      onChange={(on) => setUsers((list) => list.map((x) => (x.id === u.id ? { ...x, on } : x)))} />
  ))
}

function RelationRow({ user, kind, onChange }) {
  const notify = useNotice()
  const [busy, setBusy] = useState(false)
  const word = kind === 'mute' ? 'ミュート' : 'ブロック'

  async function toggle() {
    setBusy(true)
    try {
      await setRelation(user, kind, !user.on)
      onChange(!user.on)
      notify(relationMessage(user.handle, kind, !user.on))
    } catch (e) {
      notify(e.message, { error: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="user-row">
      <Avatar user={user} />
      <div className="info">
        <Link className="name" to={`/users/${user.handle}`}>
          {user.displayName}
        </Link>{' '}
        <span className="handle">@{user.handle}</span>
        {!user.on && <p className="released">解除しました</p>}
      </div>
      <button className="btn btn-outline" type="button" disabled={busy} onClick={toggle}
        aria-label={user.on ? `@${user.handle} さんの${word}を解除` : `@${user.handle} さんをもう一度${word}する`}>
        {user.on ? `${word}を解除` : `もう一度${word}する`}
      </button>
    </div>
  )
}
