import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useAttendees } from './queries'
import type { Event } from './types'
import { Button } from '../../components/Button'

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

/** Board-side check-in tools for an attendance-enabled event: QR, live count, CSV export. */
export function AttendancePanel({ event }: { event: Event }) {
    const { data: attendees = [], isLoading } = useAttendees(event.id, true)
    const attendUrl = `${window.location.origin}/events/${event.id}/attend`
    const [showList, setShowList] = useState(false)

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
        <div className="rounded-lg border border-line bg-bg-raised p-5">
            <div className="flex flex-wrap items-start gap-6">
                <div className="rounded-lg bg-white p-3">
                    <QRCodeSVG value={attendUrl} size={150} bgColor="#ffffff" fgColor="#0c0e13" />
                </div>
                <div className="min-w-0 flex-1">
                    <p className="m-0 text-[0.9rem] font-semibold">Attendance check-in</p>
                    <p className="mb-2 mt-1 font-mono text-[0.78rem] text-text-soft">
                        Display this QR at the event. Check-in is open{' '}
                        {time(event.startsAt - 60 * 60 * 1000)}–
                        {time(event.startsAt + 2 * 60 * 60 * 1000)}.
                    </p>
                    <a
                        href={attendUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="break-all font-mono text-[0.75rem]"
                    >
                        {attendUrl}
                    </a>
                    <div className="mt-4 flex flex-wrap items-center gap-4">
                        <span className="font-mono text-[0.8rem] text-text-soft">
                            {isLoading ? (
                                'Loading…'
                            ) : (
                                <>
                                    <span className="text-gold">{attendees.length}</span> checked in
                                </>
                            )}
                        </span>
                        <Button
                            variant="ghost"
                            disabled={attendees.length === 0}
                            onClick={() => setShowList((s) => !s)}
                        >
                            {showList ? 'Hide list' : 'View attendees'}
                        </Button>
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

            {showList && attendees.length > 0 && (
                <div className="mt-5 overflow-x-auto border-t border-line pt-4">
                    <table className="w-full border-collapse text-left text-[0.85rem]">
                        <thead>
                            <tr className="font-mono text-[0.72rem] uppercase tracking-[0.08em] text-text-faint">
                                <th className="py-2 pr-4 font-semibold">Name</th>
                                <th className="py-2 pr-4 font-semibold">Email</th>
                                <th className="py-2 font-semibold">Checked in</th>
                            </tr>
                        </thead>
                        <tbody>
                            {attendees.map((a, i) => (
                                <tr key={`${a.email}-${i}`} className="border-t border-line-soft">
                                    <td className="py-2 pr-4">{a.name}</td>
                                    <td className="py-2 pr-4 text-text-soft">{a.email}</td>
                                    <td className="py-2 font-mono text-[0.8rem] text-text-soft">
                                        {new Date(a.recordedAt).toLocaleString()}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    )
}
