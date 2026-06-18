import { request } from '../../lib/request'
import type { LoginResponse, User } from './types'

export const authApi = {
    loginWithGoogle: (credential: string) =>
        request<LoginResponse>('/api/auth/google', { method: 'POST', body: { credential } }),
    me: (token: string) => request<User>('/api/me', { token }),
    updateProfile: (
        token: string,
        profile: {
            joinedTerm?: string | null
            gradYear?: number | null
            github?: string | null
            linkedin?: string | null
            website?: string | null
            company?: string | null
            listed?: boolean
        },
    ) => request<User>('/api/me', { method: 'POST', body: profile, token }),
}
