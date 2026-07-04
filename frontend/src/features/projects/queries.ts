import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import type { DesignDoc } from '../design/types'
import {
    acceptInvite,
    createProject,
    declineInvite,
    getProjectDetail,
    getPresentationDates,
    getProjectShowcase,
    listMyInvites,
    listMyProjects,
    listProjects,
    resubmitProject,
    toggleProjectLike,
    updateProjectDesign,
    updateProjectMembers,
    updateProjectTasks,
} from './api'
import type { CreateProjectRequest, Project, ProjectShowcase, TaskInput } from './types'

export const projectKeys = {
    all: ['projects'] as const,
    mine: ['projects', 'mine'] as const,
    invites: ['projects', 'invites'] as const,
    presentationDates: ['projects', 'presentation-dates'] as const,
    detail: (id: string) => ['projects', id] as const,
    showcase: (id: string) => ['projects', id, 'showcase'] as const,
}

export function usePresentationDates() {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.presentationDates,
        queryFn: () => getPresentationDates(token!),
        enabled: !!token,
    })
}

export function useProjectShowcase(id: string) {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.showcase(id),
        queryFn: () => getProjectShowcase(id, token),
        enabled: !!id,
    })
}

export function useProjects() {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.all,
        queryFn: () => listProjects(token),
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

export function useMyInvites() {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.invites,
        queryFn: () => listMyInvites(token!),
        enabled: !!token,
    })
}

export function useRespondToInvite() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({ id, accept }: { id: string; accept: boolean }) =>
            accept ? acceptInvite(token!, id) : declineInvite(token!, id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: projectKeys.invites })
            queryClient.invalidateQueries({ queryKey: projectKeys.mine })
        },
    })
}

export function useProjectDetail(id: string) {
    const { token } = useAuth()
    return useQuery({
        queryKey: projectKeys.detail(id),
        queryFn: () => getProjectDetail(token!, id),
        enabled: !!token && !!id,
    })
}

export function useToggleLike() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (id: string) => toggleProjectLike(token!, id),
        onSuccess: (result, id) => {
            queryClient.setQueryData<Project[]>(projectKeys.all, (prev) =>
                prev?.map((p) =>
                    p.id === id ? { ...p, likes: result.likes, liked: result.liked } : p,
                ),
            )
            queryClient.setQueryData<ProjectShowcase>(projectKeys.showcase(id), (prev) =>
                prev
                    ? {
                          ...prev,
                          project: { ...prev.project, likes: result.likes, liked: result.liked },
                      }
                    : prev,
            )
        },
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
    imageUrl?: string
    designDoc: DesignDoc
}

export function useUpdateProjectDesign(id: string) {
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

export function useUpdateProjectMembers(id: string) {
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

export function useResubmitProject(id: string) {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: () => resubmitProject(token!, id),
        onSuccess: (updated) => {
            queryClient.setQueryData(projectKeys.detail(id), updated)
            queryClient.invalidateQueries({ queryKey: projectKeys.all })
            queryClient.invalidateQueries({ queryKey: projectKeys.mine })
        },
    })
}

export function useUpdateProjectTasks(id: string) {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (tasks: TaskInput[]) => updateProjectTasks(token!, id, tasks),
        onSuccess: (updated) => {
            queryClient.setQueryData(projectKeys.detail(id), updated)
        },
    })
}
