import { request } from '../../lib/request'
import type { Role, User } from '../auth/types'
import type { PendingProject } from '../projects/types'
import type { BoardConfig, Employer, MemberProfile } from './types'

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

export const deleteProject = (token: string, id: string) =>
    request<void>(`/api/board/projects/${id}`, { method: 'DELETE', token })

export const getBoardSettings = (token: string) =>
    request<BoardConfig>('/api/board/settings', { token })

export const updateBoardSettings = (token: string, config: BoardConfig) =>
    request<BoardConfig>('/api/board/settings', { method: 'PUT', body: config, token })

/** The footer's meeting line. Public — the footer renders for signed-out visitors too. */
export const getFooterText = () =>
    request<{ footerText: string }>('/api/site/footer').then((r) => r.footerText)

export const listBoardMembers = (token: string) => request<User[]>('/api/board/members', { token })

export const updateMemberRole = (token: string, id: string, role: Role) =>
    request<User>(`/api/board/members/${id}/role`, { method: 'PUT', body: { role }, token })

export const updateMemberTitle = (token: string, id: string, title: string | null) =>
    request<User>(`/api/board/members/${id}/title`, { method: 'PUT', body: { title }, token })

export const updateMemberProfile = (token: string, id: string, profile: MemberProfile) =>
    request<User>(`/api/board/members/${id}/profile`, { method: 'PUT', body: profile, token })

/** The employers cleared for the home page. Public — the home page renders for everyone. */
export const getVisibleEmployers = () => request<string[]>('/api/site/employers')

export const listEmployers = (token: string) =>
    request<Employer[]>('/api/board/employers', { token })

export const addEmployer = (token: string, name: string) =>
    request<Employer[]>('/api/board/employers', { method: 'POST', body: { name }, token })

export const setEmployerVisible = (
    token: string,
    change: { name: string; visible: boolean },
) => request<Employer[]>('/api/board/employers', { method: 'PUT', body: change, token })

export const removeEmployer = (token: string, key: string) =>
    request<Employer[]>(`/api/board/employers/${encodeURIComponent(key)}`, {
        method: 'DELETE',
        token,
    })
