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
    /** Repeats weekly on `startsAt`'s weekday and time; `startsAt` is the next occurrence. */
    recurring: boolean
    /** Whether this event posts to Discord at noon on the day it happens. */
    announce: boolean
    /** When the event was posted to Discord, or null if it hasn't been yet. */
    announcedAt: number | null
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
    recurring?: boolean
    /** Post this event to the board's Discord channel at noon on the day it happens. */
    announce?: boolean
}

export interface AttendResult {
    status: 'RECORDED' | 'ALREADY'
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
