import type { ProjectTask } from '../projects/types'

export interface EditableTask {
    name: string
    assigneeIds: string[]
    dueDate: string
    /** Indices into the task list. */
    dependsOn: number[]
    milestone: boolean
}

export const requiredMilestones = (): EditableTask[] => [
    { name: 'MVP Presentation', assigneeIds: [], dueDate: '', dependsOn: [], milestone: true },
    { name: 'Final Presentation', assigneeIds: [], dueDate: '', dependsOn: [], milestone: true },
]

export function tasksToEditable(tasks: ProjectTask[]): EditableTask[] {
    const indexById = new Map(tasks.map((t, i) => [t.id, i]))
    return tasks.map((t) => ({
        name: t.name,
        assigneeIds: t.assigneeIds,
        dueDate: t.dueDate,
        dependsOn: t.dependsOn
            .map((id) => indexById.get(id))
            .filter((i): i is number => i !== undefined),
        milestone: t.milestone,
    }))
}
