import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import { projectKeys } from '../projects/queries'
import type { PresentationDates } from '../projects/types'
import {
    getBoardSettings,
    listPendingProjects,
    reviewProject,
    syncMilestones,
    updateBoardSettings,
} from './api'

export const boardKeys = {
    pending: ['board', 'pending'] as const,
    settings: ['board', 'settings'] as const,
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
    id: string
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

export function useBoardSettings() {
    const { user, token } = useAuth()
    return useQuery({
        queryKey: boardKeys.settings,
        queryFn: () => getBoardSettings(token!),
        enabled: !!token && user?.role === 'BOARD',
    })
}

export function useUpdateBoardSettings() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (dates: PresentationDates) => updateBoardSettings(token!, dates),
        onSuccess: (updated) => {
            queryClient.setQueryData(boardKeys.settings, updated)
            queryClient.setQueryData(projectKeys.presentationDates, updated)
        },
    })
}

export function useSyncMilestones() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: () => syncMilestones(token!),
        onSuccess: () => {
            // Every project's deliverables may have changed.
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
        },
    })
}
