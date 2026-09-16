export interface DesignDoc {
    // About your Project
    utilization: string
    services: string
    accessLocation: string
    intendedUsers: string
    goal: string
    usefulness: string
    demographic: string
    impact: string
    differentiation: string
    niceToHaves: string
    // Architecture
    serverNeeds: string
    databaseNeeds: string
    dataSchema: string
    dataProcurement: string
    dataProcessing: string
    softwareStack: string
    // Teamwork
    contributionExpectations: string
    communicationPlan: string
}

export const emptyDesignDoc = (): DesignDoc => ({
    utilization: '',
    services: '',
    accessLocation: '',
    intendedUsers: '',
    goal: '',
    usefulness: '',
    demographic: '',
    impact: '',
    differentiation: '',
    niceToHaves: '',
    serverNeeds: '',
    databaseNeeds: '',
    dataSchema: '',
    dataProcurement: '',
    dataProcessing: '',
    softwareStack: '',
    contributionExpectations: '',
    communicationPlan: '',
})

/** The check-in a returning project files at the start of every semester after its first. */
export interface ReturningDoc {
    // Last semester
    accomplishments: string
    unfinished: string
    challenges: string
    // This semester
    goals: string
    scopeChanges: string
    architectureChanges: string
    // Teamwork
    teamChanges: string
    contributionExpectations: string
    communicationPlan: string
}

export const emptyReturningDoc = (): ReturningDoc => ({
    accomplishments: '',
    unfinished: '',
    challenges: '',
    goals: '',
    scopeChanges: '',
    architectureChanges: '',
    teamChanges: '',
    contributionExpectations: '',
    communicationPlan: '',
})

export type DesignDocKind = 'INITIAL' | 'RETURNING'

interface FiledDoc {
    id: string
    semester: string
    submittedAt: number
}

/** A project's opening proposal, filed for the semester it started in. */
export interface InitialDocEntry extends FiledDoc {
    kind: 'INITIAL'
    content: DesignDoc
}

/** A returning project's check-in, filed at the start of every semester after its first. */
export interface ReturningDocEntry extends FiledDoc {
    kind: 'RETURNING'
    content: ReturningDoc
}

/**
 * One semester's design doc. The two specs ask different questions, so they arrive as separate
 * shapes tagged with `kind` — narrowing on it gives you the answers that spec actually has.
 */
export type DesignDocEntry = InitialDocEntry | ReturningDocEntry
