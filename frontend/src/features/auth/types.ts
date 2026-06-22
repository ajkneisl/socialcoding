export type Role = 'MEMBER' | 'BOARD'

export interface User {
    id: string
    email: string
    name: string
    role: Role
    joinedTerm: string | null
    gradYear: number | null
    github: string | null
    linkedin: string | null
    website: string | null
    company: string | null
    title?: string | null
    avatarUrl?: string | null
    listed: boolean
}

export interface LoginResponse {
    token: string
    user: User
}
