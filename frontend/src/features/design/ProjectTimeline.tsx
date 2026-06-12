import type { ProjectMember, ProjectTask } from '../projects/types'
import { Badge } from '../../components/Badge'

function formatDate(date: string) {
    return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    })
}

function dotClass(variant: 'past' | 'milestone' | 'today' | 'upcoming') {
    const colors = {
        past: 'border-green-400 bg-green-400',
        milestone: 'border-gold bg-gold/12',
        today: 'border-crimson bg-crimson',
        upcoming: 'border-text-faint bg-panel',
    }[variant]
    return `absolute left-0 top-[0.35rem] h-4 w-4 rounded-full border-2 ${colors}`
}

export function ProjectTimeline({
    tasks,
    members,
}: {
    tasks: ProjectTask[]
    members: ProjectMember[]
}) {
    const nameById = new Map(members.map((m) => [m.id, m.name]))
    const taskById = new Map(tasks.map((t) => [t.id, t]))
    const dated = tasks.filter((t) => t.dueDate).sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    const undated = tasks.filter((t) => !t.dueDate)

    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
        now.getDate(),
    ).padStart(2, '0')}`
    const todayIndex = dated.findIndex((t) => t.dueDate >= today)

    if (tasks.length === 0) {
        return <p className="text-text-soft">No deliverables yet.</p>
    }

    function renderTask(task: ProjectTask, past: boolean) {
        const assignees = task.assigneeIds.map((id) => nameById.get(id)).filter(Boolean)
        const deps = task.dependsOn.map((id) => taskById.get(id)?.name).filter(Boolean)
        return (
            <li key={task.id} className="relative pb-5 pl-8 last:pb-0">
                <span
                    className={dotClass(task.milestone ? 'milestone' : past ? 'past' : 'upcoming')}
                    aria-hidden="true"
                />
                <div className={past ? 'opacity-65' : undefined}>
                    <div className="flex flex-wrap items-center gap-[0.6rem]">
                        <strong>{task.name}</strong>
                        {task.milestone && <Badge variant="pending">milestone</Badge>}
                        <span className="font-mono text-[0.8rem] text-text-soft">
                            {task.dueDate ? formatDate(task.dueDate) : 'date TBD'}
                        </span>
                    </div>
                    {(assignees.length > 0 || deps.length > 0) && (
                        <p className="mb-0 mt-[0.15rem] font-mono text-[0.8rem] text-text-soft">
                            {assignees.length > 0 && <>assigned to {assignees.join(', ')}</>}
                            {assignees.length > 0 && deps.length > 0 && ' · '}
                            {deps.length > 0 && <>after {deps.join(', ')}</>}
                        </p>
                    )}
                </div>
            </li>
        )
    }

    const before = todayIndex === -1 ? dated : dated.slice(0, todayIndex)
    const after = todayIndex === -1 ? [] : dated.slice(todayIndex)

    return (
        <ol className="relative before:absolute before:bottom-[0.4rem] before:left-[7px] before:top-[0.4rem] before:w-[2px] before:bg-line before:content-['']">
            {before.map((t) => renderTask(t, true))}
            <li className="relative pb-5 pl-8 last:pb-0">
                <span className={dotClass('today')} aria-hidden="true" />
                <div className="pt-[0.1rem] font-mono text-[0.8rem] font-semibold text-crimson-bright">
                    today · {formatDate(today)}
                </div>
            </li>
            {after.map((t) => renderTask(t, false))}
            {undated.map((t) => renderTask(t, false))}
        </ol>
    )
}
