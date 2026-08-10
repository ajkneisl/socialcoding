import { request } from '../../lib/request'
import type { Role, User } from '../auth/types'
import type { PendingProject } from '../projects/types'
import type { BoardConfig } from './types'

export const listPendingProjects = (token: string) =>
    request<PendingProject[]>('/api/board/projects', { token })

export const reviewProject = (
    token: string,
    id: string,
    decision: 'approve' | 'reject' | 'activate' | 'deactivate',
    note?: string,
) =>
    request<void>(`/api/board/projects/${id}/${decision}`, {
        method: 'POST',
        body: { note: note ?? null },
        token,
    })

export const getBoardSettings = (token: string) =>
    request<BoardConfig>('/api/board/settings', { token })

export const updateBoardSettings = (token: string, config: BoardConfig) =>
    request<BoardConfig>('/api/board/settings', { method: 'PUT', body: config, token })

export const listBoardMembers = (token: string) => request<User[]>('/api/board/members', { token })

export const updateMemberRole = (token: string, id: string, role: Role) =>
    request<User>(`/api/board/members/${id}/role`, { method: 'PUT', body: { role }, token })

export const updateMemberTitle = (token: string, id: string, title: string | null) =>
    request<User>(`/api/board/members/${id}/title`, { method: 'PUT', body: { title }, token })
