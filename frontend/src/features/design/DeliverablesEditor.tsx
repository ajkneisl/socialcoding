import type {ProjectMember} from '../projects/types'
import type {EditableTask} from './tasks'

export function DeliverablesEditor({
                                       tasks,
                                       team,
                                       onChange,
                                   }: {
    tasks: EditableTask[]
    team: ProjectMember[]
    onChange: (tasks: EditableTask[]) => void
}) {
    function update(i: number, patch: Partial<EditableTask>) {
        onChange(tasks.map((t, j) => (j === i ? {...t, ...patch} : t)))
    }

    function remove(i: number) {
        onChange(
            tasks
                .filter((_, j) => j !== i)
                .map((t) => ({
                    ...t,
                    dependsOn: t.dependsOn.filter((d) => d !== i).map((d) => (d > i ? d - 1 : d)),
                })),
        )
    }

    return (
        <div className="task-editor">
            {tasks.map((task, i) => (
                <div key={i} className={`task-edit-row${task.milestone ? ' milestone' : ''}`}>
                    <div className="form-row">
                        <label>
                            Task{task.milestone && <span className="muted"> (required milestone)</span>}
                            <input
                                value={task.name}
                                disabled={task.milestone}
                                required
                                maxLength={300}
                                placeholder="e.g. Set up CI pipeline"
                                onChange={(e) => update(i, {name: e.target.value})}
                            />
                        </label>
                        <label className="task-date">
                            Due date
                            <input
                                type="date"
                                value={task.dueDate}
                                onChange={(e) => update(i, {dueDate: e.target.value})}
                            />
                        </label>
                        {!task.milestone && (
                            <button
                                type="button"
                                className="btn btn-ghost task-remove"
                                onClick={() => remove(i)}
                            >
                                Remove
                            </button>
                        )}
                    </div>
                    {team.length > 0 && (
                        <div className="chip-select">
                            <span className="mono muted">assigned:</span>
                            {team.map((m) => {
                                const on = task.assigneeIds.includes(m.id)
                                return (
                                    <button
                                        type="button"
                                        key={m.id}
                                        className={`chip${on ? ' chip-active' : ''}`}
                                        onClick={() =>
                                            update(i, {
                                                assigneeIds: on
                                                    ? task.assigneeIds.filter((a) => a !== m.id)
                                                    : [...task.assigneeIds, m.id],
                                            })
                                        }
                                    >
                                        {m.name}
                                    </button>
                                )
                            })}
                        </div>
                    )}
                    {tasks.length > 1 && (
                        <div className="chip-select">
                            <span className="mono muted">depends on:</span>
                            {tasks.map((other, j) => {
                                if (j === i) return null
                                const on = task.dependsOn.includes(j)
                                return (
                                    <button
                                        type="button"
                                        key={j}
                                        className={`chip${on ? ' chip-active' : ''}`}
                                        onClick={() =>
                                            update(i, {
                                                dependsOn: on
                                                    ? task.dependsOn.filter((d) => d !== j)
                                                    : [...task.dependsOn, j],
                                            })
                                        }
                                    >
                                        {other.name || `task ${j + 1}`}
                                    </button>
                                )
                            })}
                        </div>
                    )}
                </div>
            ))}
            <button
                type="button"
                className="btn btn-ghost"
                onClick={() =>
                    onChange([
                        ...tasks,
                        {name: '', assigneeIds: [], dueDate: '', dependsOn: [], milestone: false},
                    ])
                }
            >
                + Add task
            </button>
        </div>
    )
}
