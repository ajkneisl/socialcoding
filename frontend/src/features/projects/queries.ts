import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import type { DesignDoc } from '../design/types'
import {
    createProject,
    getProjectDetail,
    listMyProjects,
    listProjects,
    updateProjectDesign,
    updateProjectMembers,
    updateProjectTasks,
} from './api'
import type { CreateProjectRequest, TaskInput } from './types'

export const projectKeys = {
    all: ['projects'] as const,
    mine: ['projects', 'mine'] as const,
    detail: (id: number) => ['projects', id] as const,
}

export function useProjects() {
    return useQuery({
        queryKey: projectKeys.all,
        queryFn: listProjects,
    })
}

export function useMyProjects() {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.mine,
        queryFn: () => listMyProjects(token!),
        enabled: !!token,
    })
}

export function useProjectDetail(id: number) {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.detail(id),
        queryFn: () => getProjectDetail(token!, id),
        enabled: !!token && !Number.isNaN(id),
    })
}

export function useCreateProject() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (project: CreateProjectRequest) => createProject(token!, project),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
            queryClient.invalidateQueries({ queryKey: projectKeys.mine })
        },
    })
}

type DesignUpdate = {
    title: string
    description: string
    repoUrl?: string
    designDoc: DesignDoc
}

export function useUpdateProjectDesign(id: number) {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (body: DesignUpdate) => updateProjectDesign(token!, id, body),
        onSuccess: (updated) => {
            queryClient.setQueryData(projectKeys.detail(id), updated)
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
        },
    })
}

export function useUpdateProjectMembers(id: number) {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (body: { memberIds: string[]; teamLeadId: string }) =>
            updateProjectMembers(token!, id, body),
        onSuccess: (updated) => {
            if (updated) queryClient.setQueryData(projectKeys.detail(id), updated)
        },
    })
}

export function useUpdateProjectTasks(id: number) {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (tasks: TaskInput[]) => updateProjectTasks(token!, id, tasks),
        onSuccess: (updated) => {
            queryClient.setQueryData(projectKeys.detail(id), updated)
        },
    })
}
