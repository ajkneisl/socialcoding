import type { Role } from '../auth/types'

export interface Person {
    id: string
    name: string
    gradYear: number | null
    github: string | null
    linkedin: string | null
    website: string | null
    company: string | null
    role: Role
    title?: string | null
    avatarUrl?: string | null
}
