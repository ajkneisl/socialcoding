import { request } from '../../lib/request'
import type { PendingProject, PresentationDates } from '../projects/types'

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
    request<PresentationDates>('/api/board/settings', { token })

export const updateBoardSettings = (token: string, dates: PresentationDates) =>
    request<PresentationDates>('/api/board/settings', { method: 'PUT', body: dates, token })

export const syncMilestones = (token: string) =>
    request<{ projects: number }>('/api/board/projects/sync-milestones', {
        method: 'POST',
        token,
    })
