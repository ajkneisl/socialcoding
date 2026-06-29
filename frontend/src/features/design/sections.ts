import type { DesignDoc } from './types'

export interface DesignSection {
    id: string
    title: string
    blurb: string
    questions: { field: keyof DesignDoc; label: string; placeholder?: string }[]
}

export const DESIGN_SECTIONS: DesignSection[] = [
    {
        id: 'about',
        title: 'About your Project',
        blurb: 'What you are building, who it is for, and why it matters.',
        questions: [
            { field: 'utilization', label: 'What does your project do?' },
            { field: 'intendedUsers', label: 'Who is it intended for?' },
            { field: 'goal', label: 'What are the goals?' },
            {
                field: 'differentiation',
                label: 'How does your project stand out from existing technologies?',
            },
        ],
    },
    {
        id: 'architecture',
        title: 'Architecture',
        blurb: 'How the project will be hosted and how its data will be managed.',
        questions: [
            {
                field: 'serverNeeds',
                label: 'Will your project be hosted? If so, how do you plan to host it?',
            },
            { field: 'databaseNeeds', label: 'How will data be managed in your project?' },
        ],
    },
    {
        id: 'teamwork',
        title: 'Teamwork',
        blurb: 'How the team will work together week to week.',
        questions: [
            {
                field: 'contributionExpectations',
                label:
                    'What should members of the team be expected to contribute? How many hours per week ' +
                    'and will there be alternative meeting times to the traditional Social Coding project ' +
                    'meetings?',
                placeholder:
                    'Most successful projects spend approximately 2-3 additional hours outside of Social ' +
                    'Coding meetings.',
            },
            {
                field: 'communicationPlan',
                label:
                    'How will information be communicated among the group? Where should questions be ' +
                    'asked? How should team members handle absences? How will conflicts be resolved?',
            },
        ],
    },
]
