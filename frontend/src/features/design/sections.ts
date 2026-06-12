import type {DesignDoc} from './types'

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
            {field: 'utilization', label: 'How is your project intended to be utilized?'},
            {field: 'services', label: 'What services does it provide?'},
            {field: 'accessLocation', label: 'Where can it be accessed?'},
            {field: 'intendedUsers', label: 'Who is meant to access it?'},
            {field: 'goal', label: 'What is the goal of this project?'},
            {field: 'usefulness', label: 'Why would it be useful?'},
            {field: 'demographic', label: 'Which demographic would benefit/use this project?'},
            {field: 'impact', label: 'What impact would this project have?'},
            {
                field: 'differentiation',
                label: 'How does this project differ from other similar existing technologies?',
            },
            {
                field: 'niceToHaves',
                label:
                    'What ideas or aspects of your project are nice-to-haves but will not be explicitly ' +
                    'pursued for the sake of time?',
            },
        ],
    },
    {
        id: 'architecture',
        title: 'Architecture',
        blurb: 'How the project will be built, hosted, and fed with data.',
        questions: [
            {field: 'serverNeeds', label: 'Will you need a server to host your project?'},
            {field: 'databaseNeeds', label: 'Will a database be needed to store data?'},
            {field: 'dataSchema', label: 'How will data be structured (schema)?'},
            {field: 'dataProcurement', label: 'How/Where will data be procured?'},
            {field: 'dataProcessing', label: 'Will data be processed?'},
            {
                field: 'softwareStack',
                label: 'What software will be needed to make your project work?',
                placeholder: 'Discord, React, Django, Kubernetes, Docker, etc.',
            },
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
