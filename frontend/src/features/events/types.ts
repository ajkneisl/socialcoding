export interface Event {
    id: number
    title: string
    summary: string
    body: string
    startsAt: number
    location: string | null
    burrowUrl: string | null
    imageUrl: string | null
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
}
