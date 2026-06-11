import { createContext, useContext } from 'react'
import type { User } from './api'

export const TOKEN_KEY = 'sc_token'

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined

export interface AuthState {
  user: User | null
  token: string | null
  loading: boolean
  loginWithCredential: (credential: string) => Promise<void>
  refreshUser: () => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
