import { Link } from 'react-router-dom'
import { useAttendanceAnalytics } from '../../features/events/queries'
import { FormError } from '../../components/FormError'
import { SectionHead } from '../../components/SectionHead'

export default function BoardAnalytics() {
    const { data: summary = [], isLoading, error } = useAttendanceAnalytics()

    const total = summary.reduce((sum, e) => sum + e.attendees, 0)
    const max = summary.reduce((m, e) => Math.max(m, e.attendees), 0)

    return (
        <>
            <SectionHead title="Attendance analytics">
                Attendees checked in per event, across events with attendance tracking enabled.
            </SectionHead>

            <FormError error={error?.message} />

            {isLoading ? (
                <p className="text-text-soft">Loading…</p>
            ) : summary.length === 0 ? (
                <p className="text-text-soft">
                    No attendance-enabled events yet. Turn on attendance when creating an event.
                </p>
            ) : (
                <>
                    <p className="mb-6 font-mono text-[0.8rem] text-text-soft">
                        {total} total check-in{total === 1 ? '' : 's'} across {summary.length} event
                        {summary.length === 1 ? '' : 's'}
                    </p>
                    <div className="flex flex-col gap-5">
                        {summary.map((e) => (
                            <div key={e.eventId}>
                                <div className="mb-1 flex items-baseline justify-between gap-4">
                                    <Link
                                        to={`/events/${e.eventId}`}
                                        className="font-medium text-text hover:text-gold hover:no-underline"
                                    >
                                        {e.title}
                                    </Link>
                                    <span className="shrink-0 font-mono text-[0.8rem] text-text-soft">
                                        {new Date(e.startsAt).toLocaleDateString()} ·{' '}
                                        <span className="text-gold">{e.attendees}</span>
                                    </span>
                                </div>
                                <div className="h-3 overflow-hidden rounded-full bg-bg-raised">
                                    <div
                                        className="h-full rounded-full bg-gold"
                                        style={{
                                            width: `${max > 0 ? (e.attendees / max) * 100 : 0}%`,
                                        }}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}
        </>
    )
}
