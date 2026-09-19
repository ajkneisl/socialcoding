import { useEffect, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useAttendees } from './queries'
import type { Event } from './types'
import { ATTENDANCE_CLOSES_MS, ATTENDANCE_OPENS_MS } from './util'
import { Button, LinkButton } from '../../components/Button'

function downloadCsv(filename: string, rows: string[][]) {
    const escape = (v: string) => `"${v.replace(/"/g, '""')}"`
    const csv = rows.map((r) => r.map(escape).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
}

const time = (ms: number) =>
    new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })

/**
 * Board-side check-in summary for an attendance-enabled event: the QR, how many
 * have checked in, and the way through to the full screen.
 *
 * The attendee list itself lives on the standalone check-in page, so this only
 * ever shows a count.
 */
export function AttendancePanel({ event }: { event: Event }) {
    const { data: attendees = [], isLoading } = useAttendees(event.id)
    const attendUrl = `${window.location.origin}/events/${event.id}/attend`
    const [copied, setCopied] = useState(false)

    useEffect(() => {
        if (!copied) return
        const reset = setTimeout(() => setCopied(false), 1500)
        return () => clearTimeout(reset)
    }, [copied])

    async function copyUrl() {
        try {
            await navigator.clipboard.writeText(attendUrl)
            setCopied(true)
        } catch {
            // Clipboard is unavailable outside a secure context; the URL is on
            // screen to copy by hand anyway.
        }
    }

    function exportCsv() {
        const rows = [
            ['Name', 'Email', 'Checked in'],
            ...attendees.map((a) => [a.name, a.email, new Date(a.recordedAt).toLocaleString()]),
        ]
        const slug = event.title
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '')
        downloadCsv(`attendance-${slug || event.id}.csv`, rows)
    }

    return (
        <div className="rounded-xl border border-line bg-bg-raised p-5">
            <div className="flex flex-wrap items-start gap-6">
                <div className="shrink-0 rounded-lg bg-white p-2.5">
                    <QRCodeSVG value={attendUrl} size={128} bgColor="#ffffff" fgColor="#0c0e13" />
                </div>

                <div className="min-w-0 flex-1">
                    <p className="m-0 font-mono text-[0.72rem] uppercase tracking-[0.16em] text-text-faint">
                        Attendance
                    </p>

                    <p className="m-0 mt-2 flex items-baseline gap-2">
                        <span className="text-[2.6rem] font-bold leading-none text-gold">
                            {isLoading ? '—' : attendees.length}
                        </span>
                        <span className="text-[0.88rem] text-text-soft">checked in</span>
                    </p>

                    <p className="m-0 mt-2 font-mono text-[0.78rem] text-text-soft">
                        Open {time(event.startsAt + ATTENDANCE_OPENS_MS)} –{' '}
                        {time(event.startsAt + ATTENDANCE_CLOSES_MS)}
                    </p>

                    <div className="mt-4 flex items-center gap-2 rounded-lg border border-line-soft bg-bg py-2 pl-3 pr-2">
                        <span className="min-w-0 flex-1 truncate font-mono text-[0.75rem] text-text-soft">
                            {attendUrl}
                        </span>

                        <button
                            type="button"
                            onClick={copyUrl}
                            className="shrink-0 cursor-pointer rounded-md border-0 bg-transparent px-2 py-1 font-mono text-[0.72rem] uppercase tracking-[0.1em] text-text-faint transition-colors hover:text-gold"
                        >
                            {copied ? 'Copied' : 'Copy'}
                        </button>
                    </div>

                    <div className="mt-4 flex flex-wrap gap-3">
                        <LinkButton
                            to={`/events/${event.id}/checkin`}
                            target="_blank"
                            rel="noreferrer"
                        >
                            Open check-in screen
                        </LinkButton>

                        <Button
                            variant="ghost"
                            disabled={attendees.length === 0}
                            onClick={exportCsv}
                        >
                            Export CSV
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    )
}
