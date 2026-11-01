import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from './api.js'

// ログイン状態を画面全体で共有する。最初に GET /api/auth/config(CSRF 対策の Cookie も受け取る)、
// 次に GET /api/auth/me でログイン中かを確かめる(要件 F-AUTH-06)
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // loggedOut: この画面でログアウトした直後か(ログイン画面に「ログアウトしました」を出すため)
  const [state, setState] = useState({ status: 'loading', user: null, googleEnabled: false, error: null, loggedOut: false })

  const refresh = useCallback(async () => {
    try {
      const user = await api('/api/auth/me')
      setState((s) => ({ ...s, status: 'authenticated', user, error: null }))
      return user
    } catch (e) {
      if (e.status === 401) {
        setState((s) => ({ ...s, status: 'anonymous', user: null, error: null }))
        return null
      }
      setState((s) => ({ ...s, status: 'error', error: e.message }))
      return null
    }
  }, [])

  const start = useCallback(async () => {
    setState((s) => ({ ...s, status: 'loading' }))
    try {
      const config = await api('/api/auth/config')
      setState((s) => ({ ...s, googleEnabled: config.googleEnabled }))
    } catch (e) {
      setState((s) => ({ ...s, status: 'error', error: e.message }))
      return
    }
    await refresh()
  }, [refresh])

  useEffect(() => {
    start()
  }, [start])

  const setUser = useCallback((user) => {
    setState((s) => ({ ...s, status: 'authenticated', user, loggedOut: false }))
  }, [])

  // ログイン状態が消えると、ログインが必要な画面(RequireAuth)がログイン画面へ移す
  const logout = useCallback(async () => {
    await api('/api/auth/logout', { method: 'POST' })
    setState((s) => ({ ...s, status: 'anonymous', user: null, loggedOut: true }))
  }, [])

  const value = useMemo(() => ({ ...state, refresh, retry: start, setUser, logout }), [state, refresh, start, setUser, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
