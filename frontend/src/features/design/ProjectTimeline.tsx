import type {ProjectMember, ProjectTask} from '../projects/types'

function formatDate(date: string) {
    return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    })
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
        return <p className="muted">No deliverables yet.</p>
    }

    function renderTask(task: ProjectTask, past: boolean) {
        const assignees = task.assigneeIds.map((id) => nameById.get(id)).filter(Boolean)
        const deps = task.dependsOn.map((id) => taskById.get(id)?.name).filter(Boolean)
        return (
            <li
                key={task.id}
                className={`tl-item${past ? ' tl-past' : ''}${task.milestone ? ' tl-milestone' : ''}`}
            >
                <span className="tl-dot" aria-hidden="true"/>
                <div className="tl-body">
                    <div className="tl-head">
                        <strong>{task.name}</strong>
                        {task.milestone && <span className="badge badge-pending">milestone</span>}
                        <span className="mono muted">
              {task.dueDate ? formatDate(task.dueDate) : 'date TBD'}
            </span>
                    </div>
                    {(assignees.length > 0 || deps.length > 0) && (
                        <p className="mono muted tl-meta">
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
        <ol className="doc-timeline">
            {before.map((t) => renderTask(t, true))}
            <li className="tl-item tl-today">
                <span className="tl-dot" aria-hidden="true"/>
                <div className="tl-body mono">today · {formatDate(today)}</div>
            </li>
            {after.map((t) => renderTask(t, false))}
            {undated.map((t) => renderTask(t, false))}
        </ol>
    )
}
