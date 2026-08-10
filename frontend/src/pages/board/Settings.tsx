import { useState, type FormEvent } from 'react'
import { useBoardSettings, useUpdateBoardSettings } from '../../features/board/queries'
import type { BoardConfig } from '../../features/board/types'
import { Button } from '../../components/Button'
import { CheckIcon } from '../../components/CheckIcon'
import { FormError } from '../../components/FormError'
import { Spinner } from '../../components/Spinner'
import { card } from '../../components/styles'

/**
 * Save-button label reflecting a mutation's state. Both cards below PUT the whole config, so they
 * share one mutation and only the card whose fields are dirty shows "Saved".
 */
function SaveLabel({ pending, saved, idle }: { pending: boolean; saved: boolean; idle: string }) {
    if (pending) {
        return (
            <>
                <Spinner className="h-4 w-4" />
                Saving…
            </>
        )
    }
    if (saved) {
        return (
            <>
                <CheckIcon />
                Saved
            </>
        )
    }
    return <>{idle}</>
}

function SettingsForm({ initial }: { initial: BoardConfig }) {
    const update = useUpdateBoardSettings()
    const [mvpDate, setMvpDate] = useState(initial.presentationDates.mvpDate)
    const [finalDate, setFinalDate] = useState(initial.presentationDates.finalDate)
    const [channelID, setChannelID] = useState(initial.announcementChannelID)
    const [semester, setSemester] = useState(initial.currentSemester)
    const [persisted, setPersisted] = useState(initial)
    /** Which card's Save was pressed last, so only that one reports "Saved". */
    const [lastSaved, setLastSaved] = useState<'dates' | 'channel' | 'semester' | null>(null)

    const dirty =
        mvpDate !== persisted.presentationDates.mvpDate ||
        finalDate !== persisted.presentationDates.finalDate
    const channelDirty = channelID !== persisted.announcementChannelID
    const semesterDirty = semester !== persisted.currentSemester

    /**
     * Each card saves only its own fields, carrying the other card's persisted values through
     * untouched, so clicking one Save never commits half-finished edits from the other.
     */
    function save(e: FormEvent) {
        e.preventDefault()
        setLastSaved('dates')
        update.mutate(
            { ...persisted, presentationDates: { mvpDate, finalDate } },
            { onSuccess: (saved) => setPersisted(saved) },
        )
    }

    function saveChannel(e: FormEvent) {
        e.preventDefault()
        setLastSaved('channel')
        update.mutate(
            { ...persisted, announcementChannelID: channelID },
            {
                onSuccess: (saved) => {
                    setPersisted(saved)
                    // The server strips a pasted "<#123>" down to digits; show what it stored so
                    // the field doesn't read as unsaved forever.
                    setChannelID(saved.announcementChannelID)
                },
            },
        )
    }

    function saveSemester(e: FormEvent) {
        e.preventDefault()
        setLastSaved('semester')
        update.mutate(
            { ...persisted, currentSemester: semester },
            {
                onSuccess: (saved) => {
                    setPersisted(saved)
                    // Clearing the box hands back the semester the calendar says it is.
                    setSemester(saved.currentSemester)
                },
            },
        )
    }

    return (
        <div className="flex max-w-[680px] flex-col gap-8">
            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={saveSemester}>
                <div>
                    <h3 className="m-0">Current semester</h3>
                    <p className="m-0 mt-2 text-text-soft">
                        The semester projects file their design docs under.
                    </p>
                </div>

                <label className="max-w-[22rem]">
                    Semester
                    <input
                        value={semester}
                        onChange={(e) => setSemester(e.target.value)}
                        placeholder="Fall 2026"
                        maxLength={32}
                    />
                </label>

                <FormError error={lastSaved === 'semester' ? update.error?.message : null} />
                <Button
                    type="submit"
                    disabled={update.isPending || !semesterDirty}
                    className="self-start"
                >
                    <SaveLabel
                        pending={update.isPending && lastSaved === 'semester'}
                        saved={update.isSuccess && lastSaved === 'semester' && !semesterDirty}
                        idle="Save semester"
                    />
                </Button>
            </form>

            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={save}>
                <div>
                    <h3 className="m-0">Presentation dates</h3>
                    <p className="m-0 mt-2 text-text-soft">
                        Teams pick these up when they file a design doc: filing swaps last
                        semester's MVP and Final Presentation milestones for a new pair on these
                        dates. Projects that already filed keep the dates they were given.
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
                <FormError error={lastSaved === 'dates' ? update.error?.message : null} />
                <Button type="submit" disabled={update.isPending || !dirty} className="self-start">
                    <SaveLabel
                        pending={update.isPending && lastSaved === 'dates'}
                        saved={update.isSuccess && lastSaved === 'dates' && !dirty}
                        idle="Save dates"
                    />
                </Button>
            </form>

            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={saveChannel}>
                <div>
                    <h3 className="m-0">Discord announcements</h3>
                    <p className="m-0 mt-2 text-text-soft">
                        The channel events post to when you tick "Announce to Discord" while
                        publishing. Announcements go out at noon on the day of the event.
                        Right-click a channel in Discord and choose "Copy Channel ID" to get this.
                        Leave it blank to turn announcements off.
                    </p>
                </div>
                <label className="max-w-[22rem]">
                    Channel ID
                    <input
                        value={channelID}
                        onChange={(e) => setChannelID(e.target.value)}
                        placeholder="123456789012345678"
                        inputMode="numeric"
                    />
                </label>
                <FormError error={lastSaved === 'channel' ? update.error?.message : null} />
                <Button
                    type="submit"
                    disabled={update.isPending || !channelDirty}
                    className="self-start"
                >
                    <SaveLabel
                        pending={update.isPending && lastSaved === 'channel'}
                        saved={update.isSuccess && lastSaved === 'channel' && !channelDirty}
                        idle="Save channel"
                    />
                </Button>
            </form>
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
