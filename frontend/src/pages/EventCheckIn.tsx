import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { useAuth } from '../auth-context'
import { useAttendees, useEvent } from '../features/events/queries'
import { ATTENDANCE_CLOSES_MS, ATTENDANCE_OPENS_MS } from '../features/events/util'
import { Eyebrow } from '../components/Eyebrow'

/**
 * How often the attendee list re-polls. The screen is watched while a room
 * fills up, so a name should land within a few seconds of someone scanning.
 */
const REFRESH_MS = 5_000

const time = (ms: number) =>
    new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

/** Centred single-line state, for everything that isn't the live screen. */
function Shell({ children }: { children: ReactNode }) {
    return (
        <main className="flex min-h-screen items-center justify-center bg-bg p-8">
            <p className="max-w-[40ch] text-center text-text-soft">{children}</p>
        </main>
    )
}

/**
 * Standalone check-in screen for an event, meant to be put on a projector or a
 * spare monitor: a large QR to scan, a live count, and names as they arrive.
 *
 * Deliberately outside the site layout — no nav, no footer, nothing to click
 * past by accident.
 */
export default function EventCheckIn() {
    const { id } = useParams()
    const eventId = Number(id)
    const { user, loading } = useAuth()
    const { data: event, isLoading } = useEvent(eventId)

    // The attendance endpoint is board-only, so there's no point polling it for
    // anyone else.
    const isBoard = user?.role === 'BOARD'
    const { data: attendees = [] } = useAttendees(eventId, {
        enabled: isBoard && !Number.isNaN(eventId),
        refetchInterval: REFRESH_MS,
    })

    // Calling Date.now() during render is impure, so the clock ticks into state.
    // It only drives the window label, which changes at most twice a session.
    const [now, setNow] = useState(() => Date.now())
    useEffect(() => {
        const tick = setInterval(() => setNow(Date.now()), REFRESH_MS)
        return () => clearInterval(tick)
    }, [])

    if (loading || isLoading) return <Shell>Loading…</Shell>
    if (!isBoard) return <Shell>This check-in screen is for Social Coding board members.</Shell>
    if (!event) return <Shell>That event could not be found.</Shell>
    if (!event.attendance) return <Shell>Attendance isn’t enabled for “{event.title}”.</Shell>

    const attendUrl = `${window.location.origin}/events/${event.id}/attend`
    const opensAt = event.startsAt + ATTENDANCE_OPENS_MS
    const closesAt = event.startsAt + ATTENDANCE_CLOSES_MS

    const windowLabel =
        now < opensAt
            ? `Check-in opens at ${time(opensAt)}`
            : now > closesAt
              ? `Check-in closed at ${time(closesAt)}`
              : `Checking in until ${time(closesAt)}`

    // The API returns oldest first; the newest arrival is the interesting one here.
    const recent = [...attendees].sort((a, b) => b.recordedAt - a.recordedAt)

    return (
        <main className="min-h-screen bg-bg px-[4vw] py-[3vh] text-text">
            <header className="mb-[4vh] flex flex-wrap items-baseline justify-between gap-x-8 gap-y-2">
                <div className="min-w-0">
                    <Eyebrow>Check in</Eyebrow>
                    <h1 className="m-0 text-[clamp(1.5rem,3.2vw,2.75rem)] leading-tight">
                        {event.title}
                    </h1>
                </div>

                <p className="m-0 font-mono text-[clamp(0.8rem,1.1vw,1rem)] text-text-soft">
                    {windowLabel}
                </p>
            </header>

            <div className="grid items-start gap-[4vw] lg:grid-cols-2">
                <section className="flex flex-col items-center">
                    <div className="w-full max-w-[min(520px,60vh)] rounded-2xl bg-white p-[3%]">
                        {/* Rendered oversized and scaled by CSS so it stays sharp on a projector. */}
                        <QRCodeSVG
                            value={attendUrl}
                            size={512}
                            bgColor="#ffffff"
                            fgColor="#0c0e13"
                            className="h-auto w-full"
                        />
                    </div>

                    <a
                        href={attendUrl}
                        className="mt-4 break-all text-center font-mono text-[clamp(0.75rem,1.2vw,1.05rem)]"
                    >
                        {attendUrl}
                    </a>
                </section>

                <section className="min-w-0">
                    <p className="m-0 font-mono text-[0.8rem] uppercase tracking-[0.16em] text-text-faint">
                        Checked in
                    </p>

                    <p
                        aria-live="polite"
                        className="m-0 text-[clamp(3.5rem,11vw,8rem)] font-bold leading-none text-gold"
                    >
                        {attendees.length}
                    </p>

                    {recent.length === 0 ? (
                        <p className="mt-6 text-text-soft">Waiting for the first check-in…</p>
                    ) : (
                        <ul className="mt-6 max-h-[52vh] list-none overflow-y-auto p-0">
                            {recent.map((a, i) => (
                                <li
                                    key={`${a.email}-${i}`}
                                    className="flex items-baseline justify-between gap-4 border-b border-line-soft py-[0.6rem]"
                                >
                                    <span className="min-w-0 truncate text-[clamp(1rem,1.6vw,1.35rem)]">
                                        {a.name}
                                    </span>
                                    <span className="shrink-0 font-mono text-[clamp(0.75rem,1.1vw,0.95rem)] text-text-soft">
                                        {time(a.recordedAt)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>
            </div>

            <footer className="mt-[4vh]">
                <Link to="/board/events" className="font-mono text-[0.8rem] text-text-soft">
                    ← Board events
                </Link>
            </footer>
        </main>
    )
}
