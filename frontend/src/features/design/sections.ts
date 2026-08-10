import type { DesignDoc, DesignDocEntry, ReturningDoc } from './types'

export interface DesignSection<T = DesignDoc> {
    id: string
    title: string
    blurb: string
    questions: { field: keyof T; label: string; placeholder?: string }[]
}

export const DESIGN_SECTIONS: DesignSection<DesignDoc>[] = [
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

/**
 * The questions a project answers every semester after its first. A returning project already
 * pitched itself, so this asks where it actually got to and what changes now.
 */
export const RETURNING_SECTIONS: DesignSection<ReturningDoc>[] = [
    {
        id: 'last-semester',
        title: 'Last semester',
        blurb: 'Where the project actually got to.',
        questions: [
            {
                field: 'accomplishments',
                label: 'What did your team accomplish last semester?',
                placeholder: 'Features shipped, milestones hit, anything you demoed.',
            },
            {
                field: 'unfinished',
                label: "What did you plan to do that you didn't finish?",
            },
            {
                field: 'challenges',
                label: 'What got in the way, and what would you do differently?',
            },
        ],
    },
    {
        id: 'this-semester',
        title: 'This semester',
        blurb: 'What the project is setting out to do now.',
        questions: [
            { field: 'goals', label: 'What are your goals for this semester?' },
            {
                field: 'scopeChanges',
                label: 'How has the scope or direction of the project changed since your proposal?',
            },
            {
                field: 'architectureChanges',
                label: 'What is changing in your architecture or software stack, if anything?',
            },
        ],
    },
    {
        id: 'teamwork',
        title: 'Teamwork',
        blurb: 'Who is on the team now and how you will work together.',
        questions: [
            {
                field: 'teamChanges',
                label: 'How has the team changed? Who is returning, and what roles need filling?',
            },
            {
                field: 'contributionExpectations',
                label:
                    'What should members of the team be expected to contribute this semester? How many ' +
                    'hours per week, and will there be alternative meeting times to the traditional ' +
                    'Social Coding project meetings?',
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

/**
 * The sections that match a doc's spec, so a doc can be rendered without knowing its kind. Typed
 * against both specs at once — a section only ever names fields from its own — so that reading an
 * answer by field type-checks.
 */
export function sectionsFor(entry: DesignDocEntry): DesignSection<DesignDoc & ReturningDoc>[] {
    return entry.kind === 'INITIAL' ? DESIGN_SECTIONS : RETURNING_SECTIONS
}

/** A doc's answers, whichever spec it follows. */
export function answersOf(entry: DesignDocEntry): Partial<DesignDoc & ReturningDoc> {
    return (entry.kind === 'INITIAL' ? entry.initial : entry.returning) ?? {}
}
