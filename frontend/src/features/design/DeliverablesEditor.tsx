import type { ProjectMember } from '../projects/types'
import type { EditableTask } from './tasks'
import { Button } from '../../components/Button'
import { Chip } from '../../components/Chip'

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
        onChange(tasks.map((t, j) => (j === i ? { ...t, ...patch } : t)))
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
        <div className="flex flex-col gap-[0.9rem]">
            {tasks.map((task, i) => (
                <div
                    key={i}
                    className={`flex flex-col gap-[0.6rem] rounded-xl border border-line bg-bg-raised px-[1.1rem] py-4 ${
                        task.milestone ? 'border-l-[3px] border-l-gold' : ''
                    }`}
                >
                    <div className="flex flex-wrap items-end gap-[0.9rem]">
                        <label className="min-w-[11rem]">
                            Task
                            {task.milestone && (
                                <span className="text-text-soft"> (required milestone)</span>
                            )}
                            <input
                                value={task.name}
                                disabled={task.milestone}
                                required
                                maxLength={300}
                                placeholder="e.g. Set up CI pipeline"
                                onChange={(e) => update(i, { name: e.target.value })}
                            />
                        </label>
                        <label className="flex-[0_0_170px]">
                            Due date
                            <input
                                type="date"
                                value={task.dueDate}
                                onChange={(e) => update(i, { dueDate: e.target.value })}
                            />
                        </label>
                        {!task.milestone && (
                            <Button variant="ghost" className="flex-none" onClick={() => remove(i)}>
                                Remove
                            </Button>
                        )}
                    </div>
                    {team.length > 0 && (
                        <div className="flex flex-wrap items-center gap-[0.4rem]">
                            <span className="font-mono text-[0.8rem] text-text-soft">
                                assigned:
                            </span>
                            {team.map((m) => {
                                const on = task.assigneeIds.includes(m.id)
                                return (
                                    <Chip
                                        key={m.id}
                                        active={on}
                                        sm
                                        onClick={() =>
                                            update(i, {
                                                assigneeIds: on
                                                    ? task.assigneeIds.filter((a) => a !== m.id)
                                                    : [...task.assigneeIds, m.id],
                                            })
                                        }
                                    >
                                        {m.name}
                                    </Chip>
                                )
                            })}
                        </div>
                    )}
                    {tasks.length > 1 && (
                        <div className="flex flex-wrap items-center gap-[0.4rem]">
                            <span className="font-mono text-[0.8rem] text-text-soft">
                                depends on:
                            </span>
                            {tasks.map((other, j) => {
                                if (j === i) return null
                                const on = task.dependsOn.includes(j)
                                return (
                                    <Chip
                                        key={j}
                                        active={on}
                                        sm
                                        onClick={() =>
                                            update(i, {
                                                dependsOn: on
                                                    ? task.dependsOn.filter((d) => d !== j)
                                                    : [...task.dependsOn, j],
                                            })
                                        }
                                    >
                                        {other.name || `task ${j + 1}`}
                                    </Chip>
                                )
                            })}
                        </div>
                    )}
                </div>
            ))}
            <Button
                variant="ghost"
                className="self-start"
                onClick={() =>
                    onChange([
                        ...tasks,
                        { name: '', assigneeIds: [], dueDate: '', dependsOn: [], milestone: false },
                    ])
                }
            >
                + Add task
            </Button>
        </div>
    )
}
