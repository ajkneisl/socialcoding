import { request } from '../../lib/request'
import type { DesignDoc } from '../design/types'
import type { CreateProjectRequest, Project, ProjectDetail, TaskInput } from './types'

export const projectsApi = {
    list: () => request<Project[]>('/api/projects'),
    mine: (token: string) => request<Project[]>('/api/projects/mine', { token }),
    detail: (token: string, id: number) => request<ProjectDetail>(`/api/projects/${id}`, { token }),
    create: (token: string, project: CreateProjectRequest) =>
        request<ProjectDetail>('/api/projects', { method: 'POST', body: project, token }),
    updateDesign: (
        token: string,
        id: number,
        body: { title: string; description: string; repoUrl?: string; designDoc: DesignDoc },
    ) => request<ProjectDetail>(`/api/projects/${id}/design`, { method: 'PUT', body, token }),
    updateMembers: (token: string, id: number, body: { memberIds: number[]; teamLeadId: number }) =>
        request<ProjectDetail>(`/api/projects/${id}/members`, { method: 'PUT', body, token }),
    updateTasks: (token: string, id: number, tasks: TaskInput[]) =>
        request<ProjectDetail>(`/api/projects/${id}/tasks`, {
            method: 'PUT',
            body: { tasks },
            token,
        }),
}
