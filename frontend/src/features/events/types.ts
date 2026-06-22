export interface Event {
    id: number
    title: string
    summary: string
    body: string
    startsAt: number
    location: string | null
    burrowUrl: string | null
    imageUrl: string | null
    attendance: boolean
    authorName: string
    createdAt: number
}

export interface CreateEventRequest {
    title: string
    summary: string
    body?: string
    startsAt: number
    location?: string
    burrowUrl?: string
    imageUrl?: string
    attendance?: boolean
}

export interface AttendResult {
    status: 'recorded' | 'already'
    attendees: number
}

export interface Attendee {
    name: string
    email: string
    recordedAt: number
}

export interface EventAttendanceSummary {
    eventId: number
    title: string
    startsAt: number
    attendees: number
}
