import { request } from '../../lib/request'
import type {
    AttendResult,
    Attendee,
    CreateEventRequest,
    Event,
    EventAttendanceSummary,
} from './types'

export const listEvents = () => request<Event[]>('/api/events')

export const createEvent = (token: string, event: CreateEventRequest) =>
    request<Event>('/api/events', { method: 'POST', body: event, token })

export const updateEvent = (token: string, id: number, event: CreateEventRequest) =>
    request<Event>(`/api/events/${id}`, { method: 'PUT', body: event, token })

export const deleteEvent = (token: string, id: number) =>
    request<void>(`/api/events/${id}`, { method: 'DELETE', token })

export const attendEvent = (token: string, id: number) =>
    request<AttendResult>(`/api/events/${id}/attend`, { method: 'POST', token })

export const listAttendees = (token: string, id: number) =>
    request<Attendee[]>(`/api/events/${id}/attendance`, { token })

export const getAttendanceAnalytics = (token: string) =>
    request<EventAttendanceSummary[]>('/api/events/analytics', { token })
