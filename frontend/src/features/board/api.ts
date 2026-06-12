import { request } from '../../lib/request'
import type { Project } from '../projects/types'

export const boardApi = {
    pendingProjects: (token: string) => request<Project[]>('/api/board/projects', { token }),
    reviewProject: (
        token: string,
        id: number,
        decision: 'approve' | 'reject' | 'activate' | 'deactivate',
        note?: string,
    ) =>
        request<void>(`/api/board/projects/${id}/${decision}`, {
            method: 'POST',
            body: { note: note ?? null },
            token,
        }),
}
