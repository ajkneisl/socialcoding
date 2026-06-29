import { request } from '../../lib/request'
import type { DesignDoc } from '../design/types'
import type {
    CreateProjectRequest,
    LikeResult,
    PresentationDates,
    Project,
    ProjectDetail,
    ProjectShowcase,
    TaskInput,
} from './types'

export const listProjects = (token?: string | null) =>
    request<Project[]>('/api/projects', { token })

export const listMyProjects = (token: string) => request<Project[]>('/api/projects/mine', { token })

export const listMyInvites = (token: string) =>
    request<Project[]>('/api/projects/invites', { token })

export const acceptInvite = (token: string, id: string) =>
    request<void>(`/api/projects/${id}/invite/accept`, { method: 'POST', token })

export const declineInvite = (token: string, id: string) =>
    request<void>(`/api/projects/${id}/invite/decline`, { method: 'POST', token })

export const getProjectShowcase = (id: string, token?: string | null) =>
    request<ProjectShowcase>(`/api/projects/${id}/showcase`, { token })

export const getProjectDetail = (token: string, id: string) =>
    request<ProjectDetail>(`/api/projects/${id}`, { token })

export const createProject = (token: string, project: CreateProjectRequest) =>
    request<ProjectDetail>('/api/projects', { method: 'POST', body: project, token })

export const toggleProjectLike = (token: string, id: string) =>
    request<LikeResult>(`/api/projects/${id}/like`, { method: 'POST', token })

export const updateProjectDesign = (
    token: string,
    id: string,
    body: { title: string; description: string; repoUrl?: string; designDoc: DesignDoc },
) => request<ProjectDetail>(`/api/projects/${id}/design`, { method: 'PUT', body, token })

export const updateProjectMembers = (
    token: string,
    id: string,
    body: { memberIds: string[]; teamLeadId: string },
) => request<ProjectDetail>(`/api/projects/${id}/members`, { method: 'PUT', body, token })

export const updateProjectTasks = (token: string, id: string, tasks: TaskInput[]) =>
    request<ProjectDetail>(`/api/projects/${id}/tasks`, { method: 'PUT', body: { tasks }, token })

export const resubmitProject = (token: string, id: string) =>
    request<ProjectDetail>(`/api/projects/${id}/resubmit`, { method: 'POST', token })

export const getPresentationDates = (token: string) =>
    request<PresentationDates>('/api/projects/presentation-dates', { token })
