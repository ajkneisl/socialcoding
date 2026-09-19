import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import type { Role, User } from '../auth/types'
import { projectKeys } from '../projects/queries'
import type { BoardConfig } from './types'
import {
    deleteProject,
    getBoardSettings,
    getFooterText,
    listBoardMembers,
    listPendingProjects,
    reviewProject,
    updateBoardSettings,
    updateMemberRole,
    updateMemberTitle,
} from './api'

export const boardKeys = {
    pending: ['board', 'pending'] as const,
    settings: ['board', 'settings'] as const,
    members: ['board', 'members'] as const,
    footer: ['site', 'footer'] as const,
}

/**
 * The footer's meeting line, or '' when the board hasn't set one. Runs for everyone, signed in or
 * not, so it stays cached across navigation rather than refetching on every page.
 */
export function useFooterText() {
    return useQuery({
        queryKey: boardKeys.footer,
        queryFn: getFooterText,
        staleTime: 5 * 60 * 1000,
    })
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

export function useDeleteProject() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (id: string) => deleteProject(token!, id),
        onSuccess: (_data, id) => {
            queryClient.invalidateQueries({ queryKey: boardKeys.pending })
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
            queryClient.removeQueries({ queryKey: projectKeys.detail(id) })
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
        mutationFn: (config: BoardConfig) => updateBoardSettings(token!, config),
        onSuccess: (updated) => {
            queryClient.setQueryData(boardKeys.settings, updated)
            queryClient.setQueryData(projectKeys.presentationDates, updated.presentationDates)
            // The footer is rendered from its own public query, so push the new line into it.
            queryClient.setQueryData(boardKeys.footer, updated.footerText)
        },
    })
}

export function useBoardMembers() {
    const { user, token } = useAuth()
    return useQuery({
        queryKey: boardKeys.members,
        queryFn: () => listBoardMembers(token!),
        enabled: !!token && user?.role === 'BOARD',
    })
}

export function useUpdateMemberRole() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({ id, role }: { id: string; role: Role }) =>
            updateMemberRole(token!, id, role),
        onSuccess: (updated) => {
            queryClient.setQueryData<User[]>(boardKeys.members, (prev) =>
                prev?.map((u) => (u.id === updated.id ? updated : u)),
            )
        },
    })
}

export function useUpdateMemberTitle() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({ id, title }: { id: string; title: string | null }) =>
            updateMemberTitle(token!, id, title),
        onSuccess: (updated) => {
            queryClient.setQueryData<User[]>(boardKeys.members, (prev) =>
                prev?.map((u) => (u.id === updated.id ? updated : u)),
            )
        },
    })
}
