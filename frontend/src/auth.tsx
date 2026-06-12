import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from './features/auth/api'
import type { User } from './features/auth/types'
import { AuthContext, TOKEN_KEY } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(!!token)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    authApi
      .me(token)
      .then((u) => {
        if (!cancelled) setUser(u)
      })
      .catch(() => {
        if (!cancelled) {
          localStorage.removeItem(TOKEN_KEY)
          setToken(null)
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const loginWithCredential = useCallback(async (credential: string) => {
    const res = await authApi.loginWithGoogle(credential)
    localStorage.setItem(TOKEN_KEY, res.token)
    setUser(res.user)
    setToken(res.token)
  }, [])

  const refreshUser = useCallback(async () => {
    if (!token) return
    setUser(await authApi.me(token))
  }, [token])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, token, loading, loginWithCredential, refreshUser, logout }),
    [user, token, loading, loginWithCredential, refreshUser, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
