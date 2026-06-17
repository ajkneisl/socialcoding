import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth, meKey } from '../../auth-context'
import { updateProfile } from './api'

type ProfileUpdate = {
    joinedTerm?: string | null
    gradYear?: number | null
    github?: string | null
    linkedin?: string | null
    website?: string | null
    company?: string | null
}

export function useUpdateProfile() {
    const { token } = useAuth()

    const queryClient = useQueryClient()

    return useMutation({
        mutationFn: (profile: ProfileUpdate) => updateProfile(token!, profile),
        onSuccess: (user) => {
            queryClient.setQueryData(meKey, user)
        },
    })
}
