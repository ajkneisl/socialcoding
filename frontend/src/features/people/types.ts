import type {Role} from '../auth/types'

export interface Person {
    id: number
    name: string
    joinedTerm: string | null
    gradYear: number | null
    github: string | null
    linkedin: string | null
    website: string | null
    company: string | null
    role: Role
    title?: string | null
    avatarUrl?: string | null
}
