import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth-context'
import {
    attendEvent,
    createEvent,
    deleteEvent,
    getAttendanceAnalytics,
    listAttendees,
    listEvents,
    updateEvent,
} from './api'
import type { CreateEventRequest } from './types'

export const eventKeys = {
    all: ['events'] as const,
    analytics: ['events', 'analytics'] as const,
}

export function useEvents() {
    return useQuery({
        queryKey: eventKeys.all,
        queryFn: listEvents,
    })
}

/** A single event, resolved from the cached list so detail pages share one fetch. */
export function useEvent(id: number) {
    const query = useEvents()
    return {
        ...query,
        data: query.data?.find((e) => e.id === id),
    }
}

export function useCreateEvent() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (event: CreateEventRequest) => createEvent(token!, event),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: eventKeys.all })
        },
    })
}

export function useUpdateEvent() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({ id, event }: { id: number; event: CreateEventRequest }) =>
            updateEvent(token!, id, event),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: eventKeys.all })
        },
    })
}

export function useDeleteEvent() {
    const { token } = useAuth()
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: (id: number) => deleteEvent(token!, id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: eventKeys.all })
        },
    })
}

export function useAttendEvent() {
    const { token } = useAuth()
    return useMutation({
        mutationFn: (id: number) => attendEvent(token!, id),
    })
}

export function useAttendees(id: number, enabled: boolean) {
    const { token } = useAuth()
    return useQuery({
        queryKey: [...eventKeys.all, id, 'attendees'],
        queryFn: () => listAttendees(token!, id),
        enabled: enabled && !!token,
    })
}

export function useAttendanceAnalytics() {
    const { user, token } = useAuth()
    return useQuery({
        queryKey: eventKeys.analytics,
        queryFn: () => getAttendanceAnalytics(token!),
        enabled: !!token && user?.role === 'BOARD',
    })
}
