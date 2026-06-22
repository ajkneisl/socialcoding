import { request } from '../../lib/request'
import type { DesignDoc } from '../design/types'
import type { CreateProjectRequest, Project, ProjectDetail, TaskInput } from './types'

export const listProjects = () => request<Project[]>('/api/projects')

export const listMyProjects = (token: string) => request<Project[]>('/api/projects/mine', { token })

export const getProjectDetail = (token: string, id: number) =>
    request<ProjectDetail>(`/api/projects/${id}`, { token })

export const createProject = (token: string, project: CreateProjectRequest) =>
    request<ProjectDetail>('/api/projects', { method: 'POST', body: project, token })

export const updateProjectDesign = (
    token: string,
    id: number,
    body: { title: string; description: string; repoUrl?: string; designDoc: DesignDoc },
) => request<ProjectDetail>(`/api/projects/${id}/design`, { method: 'PUT', body, token })

export const updateProjectMembers = (
    token: string,
    id: number,
    body: { memberIds: string[]; teamLeadId: string },
) => request<ProjectDetail>(`/api/projects/${id}/members`, { method: 'PUT', body, token })

export const updateProjectTasks = (token: string, id: number, tasks: TaskInput[]) =>
    request<ProjectDetail>(`/api/projects/${id}/tasks`, { method: 'PUT', body: { tasks }, token })
