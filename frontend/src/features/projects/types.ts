import type { DesignDoc } from '../design/types'

export type ProjectStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Project {
    id: string
    title: string
    description: string
    longDescription: string | null
    repoUrl: string | null
    siteUrl: string | null
    status: ProjectStatus
    active: boolean
    teamLeadName: string
    teamLeadAvatarUrl: string | null
    submittedAt: number
    reviewNote?: string | null
    likes: number
    liked: boolean
}

/** Result of toggling a heart on a project. */
export interface LikeResult {
    liked: boolean
    likes: number
}

export interface ProjectTask {
    id: number
    name: string
    assigneeIds: string[]
    dueDate: string
    dependsOn: number[]
    milestone: boolean
}

/** Task as sent to the API; dependencies are indices into the submitted list. */
export interface TaskInput {
    name: string
    assigneeIds: string[]
    dueDate: string
    dependsOn: number[]
    milestone: boolean
}

export interface ProjectMember {
    id: string
    name: string
    avatarUrl?: string | null
}

export interface ProjectDetail {
    project: Project
    designDoc: DesignDoc
    teamLeadId: string
    members: ProjectMember[]
    tasks: ProjectTask[]
    canEdit: boolean
    canManageTeam: boolean
}

/** A pending project paired with its team, for the board review queue. */
export interface PendingProject {
    project: Project
    teamLeadID: string
    members: ProjectMember[]
}

/** The public showcase view of an approved project: team and hearts, no design doc. */
export interface ProjectShowcase {
    project: Project
    teamLeadID: string
    members: ProjectMember[]
}

export interface CreateProjectRequest {
    title: string
    description: string
    repoUrl?: string
    teamLeadId?: string
    memberIds: string[]
    designDoc: DesignDoc
    tasks: TaskInput[]
}
