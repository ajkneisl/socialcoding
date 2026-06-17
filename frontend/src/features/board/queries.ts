import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import { projectKeys } from '../projects/queries'
import { listPendingProjects, reviewProject } from './api'

export const boardKeys = {
    pending: ['board', 'pending'] as const,
}

export function usePendingProjects() {
    const { user, token } = useAuth()
    return useQuery({
        queryKey: boardKeys.pending,
        queryFn: () => listPendingProjects(token!),
        enabled: !!token && user?.role === 'BOARD',
    })
}

type ReviewVars = {
    id: number
    decision: 'approve' | 'reject' | 'activate' | 'deactivate'
    note?: string
}

export function useReviewProject() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({ id, decision, note }: ReviewVars) =>
            reviewProject(token!, id, decision, note),
        onSuccess: (_data, { id }) => {
            queryClient.invalidateQueries({ queryKey: boardKeys.pending })
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
            queryClient.invalidateQueries({ queryKey: projectKeys.detail(id) })
        },
    })
}
