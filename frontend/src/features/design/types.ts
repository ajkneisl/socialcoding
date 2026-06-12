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
