import { request } from '../../lib/request'
import type { CreateEventRequest, Event } from './types'

export const listEvents = () => request<Event[]>('/api/events')

export const createEvent = (token: string, event: CreateEventRequest) =>
    request<Event>('/api/events', { method: 'POST', body: event, token })

export const deleteEvent = (token: string, id: number) =>
    request<void>(`/api/events/${id}`, { method: 'DELETE', token })
