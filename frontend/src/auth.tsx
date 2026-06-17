import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getCurrentUser, loginWithGoogle } from './features/auth/api'
import { AuthContext, TOKEN_KEY, meKey } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
    const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
    const queryClient = useQueryClient()

    const meQuery = useQuery({
        queryKey: meKey,
        // A token the server rejects is no longer useful — drop it on failure.
        queryFn: async () => {
            try {
                return await getCurrentUser(token!)
            } catch (err) {
                localStorage.removeItem(TOKEN_KEY)
                setToken(null)
                throw err
            }
        },
        enabled: !!token,
        staleTime: 5 * 60_000,
    })

    const loginWithCredential = useCallback(
        async (credential: string) => {
            const res = await loginWithGoogle(credential)
            localStorage.setItem(TOKEN_KEY, res.token)
            setToken(res.token)
            queryClient.setQueryData(meKey, res.user)
        },
        [queryClient],
    )

    const refreshUser = useCallback(async () => {
        await queryClient.invalidateQueries({ queryKey: meKey })
    }, [queryClient])

    const logout = useCallback(() => {
        localStorage.removeItem(TOKEN_KEY)
        setToken(null)
        queryClient.removeQueries({ queryKey: meKey })
    }, [queryClient])

    const user = meQuery.data ?? null
    const loading = meQuery.isLoading

    const value = useMemo(
        () => ({ user, token, loading, loginWithCredential, refreshUser, logout }),
        [user, token, loading, loginWithCredential, refreshUser, logout],
    )

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
