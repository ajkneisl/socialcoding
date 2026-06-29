import { useState, type FormEvent } from 'react'
import {
    useBoardSettings,
    useSyncMilestones,
    useUpdateBoardSettings,
} from '../../features/board/queries'
import type { PresentationDates } from '../../features/projects/types'
import { Button } from '../../components/Button'
import { CheckIcon } from '../../components/CheckIcon'
import { FormError } from '../../components/FormError'
import { Spinner } from '../../components/Spinner'
import { card } from '../../components/styles'

function SettingsForm({ initial }: { initial: PresentationDates }) {
    const update = useUpdateBoardSettings()
    const sync = useSyncMilestones()
    const [mvpDate, setMvpDate] = useState(initial.mvpDate)
    const [finalDate, setFinalDate] = useState(initial.finalDate)
    const [persisted, setPersisted] = useState(initial)

    const dirty = mvpDate !== persisted.mvpDate || finalDate !== persisted.finalDate

    function save(e: FormEvent) {
        e.preventDefault()
        update.mutate(
            { mvpDate, finalDate },
            { onSuccess: (saved) => setPersisted(saved) },
        )
    }

    return (
        <div className="flex max-w-[680px] flex-col gap-8">
            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={save}>
                <div>
                    <h3 className="m-0">Presentation dates</h3>
                    <p className="m-0 text-text-soft">
                        Every project's MVP and Final Presentation milestones inherit these dates.
                        Teams can't change them.
                    </p>
                </div>
                <div className="flex flex-wrap gap-[0.9rem] [&>label]:min-w-[11rem]">
                    <label>
                        MVP Presentation
                        <input
                            type="date"
                            value={mvpDate}
                            onChange={(e) => setMvpDate(e.target.value)}
                        />
                    </label>
                    <label>
                        Final Presentation
                        <input
                            type="date"
                            value={finalDate}
                            onChange={(e) => setFinalDate(e.target.value)}
                        />
                    </label>
                </div>
                <FormError error={update.error?.message ?? null} />
                <Button type="submit" disabled={update.isPending || !dirty} className="self-start">
                    {update.isPending ? (
                        <>
                            <Spinner className="h-4 w-4" />
                            Saving…
                        </>
                    ) : update.isSuccess && !dirty ? (
                        <>
                            <CheckIcon />
                            Saved
                        </>
                    ) : (
                        'Save dates'
                    )}
                </Button>
            </form>

            <div className={`${card} flex flex-col gap-[0.9rem]`}>
                <div>
                    <h3 className="m-0">Add tasks to projects</h3>
                    <p className="m-0 text-text-soft">
                        Re-add the MVP and Final Presentation milestones to every project using the
                        saved dates above — use this at the start of a new semester. Save your dates
                        first.
                    </p>
                </div>
                {dirty && (
                    <p className="m-0 font-mono text-[0.8rem] text-text-soft">
                        You have unsaved date changes. Save them before adding tasks.
                    </p>
                )}
                <FormError error={sync.error?.message ?? null} />
                {sync.isSuccess && (
                    <p className="m-0 text-green-400">
                        Updated milestones on {sync.data.projects}{' '}
                        {sync.data.projects === 1 ? 'project' : 'projects'}.
                    </p>
                )}
                <Button
                    variant="ghost"
                    disabled={sync.isPending || dirty}
                    onClick={() => sync.mutate()}
                    className="self-start"
                >
                    {sync.isPending ? 'Adding…' : 'Add tasks to projects'}
                </Button>
            </div>
        </div>
    )
}

export default function BoardSettings() {
    const { data, isLoading, error } = useBoardSettings()

    if (isLoading) {
        return <p className="text-text-soft">Loading…</p>
    }

    if (error || !data) {
        return <FormError error={error?.message ?? 'Could not load settings.'} />
    }

    return <SettingsForm initial={data} />
}
