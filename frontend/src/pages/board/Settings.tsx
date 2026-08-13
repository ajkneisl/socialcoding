import { useState, type FormEvent } from 'react'
import { useBoardSettings, useUpdateBoardSettings } from '../../features/board/queries'
import type { BoardConfig } from '../../features/board/types'
import { Button } from '../../components/Button'
import { CheckIcon } from '../../components/CheckIcon'
import { FormError } from '../../components/FormError'
import { Spinner } from '../../components/Spinner'
import { card } from '../../components/styles'

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
    const [footerText, setFooterText] = useState(initial.footerText)
    const [persisted, setPersisted] = useState(initial)
    const [lastSaved, setLastSaved] = useState<'dates' | 'channel' | 'semester' | 'footer' | null>(
        null,
    )

    const dirty =
        mvpDate !== persisted.presentationDates.mvpDate ||
        finalDate !== persisted.presentationDates.finalDate
    const channelDirty = channelID !== persisted.announcementChannelID
    const semesterDirty = semester !== persisted.currentSemester
    const footerDirty = footerText !== persisted.footerText

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

    function saveFooter(e: FormEvent) {
        e.preventDefault()
        setLastSaved('footer')
        update.mutate(
            { ...persisted, footerText },
            {
                onSuccess: (saved) => {
                    setPersisted(saved)
                    // Trimmed on the way in, so show what actually got stored.
                    setFooterText(saved.footerText)
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
                        The dates filled in for project's final / mvp presentation tasks.
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

            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={saveFooter}>
                <div>
                    <h3 className="m-0">Footer</h3>
                    <p className="m-0 mt-2 text-text-soft">
                        The meeting line at the bottom of every page. Leave it blank to keep the
                        default.
                    </p>
                </div>

                <label>
                    Meeting line
                    <input
                        value={footerText}
                        onChange={(e) => setFooterText(e.target.value)}
                        placeholder="Weekly Meetings · Bruininks Hall 312"
                        maxLength={120}
                    />
                </label>

                <FormError error={lastSaved === 'footer' ? update.error?.message : null} />
                <Button
                    type="submit"
                    disabled={update.isPending || !footerDirty}
                    className="self-start"
                >
                    <SaveLabel
                        pending={update.isPending && lastSaved === 'footer'}
                        saved={update.isSuccess && lastSaved === 'footer' && !footerDirty}
                        idle="Save footer"
                    />
                </Button>
            </form>

            <form className={`${card} flex flex-col gap-[0.9rem]`} onSubmit={saveChannel}>
                <div>
                    <h3 className="m-0">Discord announcements</h3>
                    <p className="m-0 mt-2 text-text-soft">
                        The channel events post to this Discord channel.
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
