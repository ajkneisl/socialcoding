import { useState } from 'react'
import type { Event } from './types'
import { dayKey } from './util'

const WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

/** Month calendar that marks days with events with a yellow dot and lets you filter by day. */
export function Calendar({
    events,
    selectedKey,
    onSelectDay,
}: {
    events: Event[]
    selectedKey: string | null
    onSelectDay: (key: string | null) => void
}) {
    const [view, setView] = useState(() => {
        const now = new Date()
        return new Date(now.getFullYear(), now.getMonth(), 1)
    })

    const eventDays = new Set(events.map((e) => dayKey(new Date(e.startsAt))))
    const todayKey = dayKey(new Date())

    const year = view.getFullYear()
    const month = view.getMonth()
    const leadingBlanks = new Date(year, month, 1).getDay()
    const daysInMonth = new Date(year, month + 1, 0).getDate()

    const cells: (Date | null)[] = []
    for (let i = 0; i < leadingBlanks; i++) cells.push(null)
    for (let d = 1; d <= daysInMonth; d++) cells.push(new Date(year, month, d))

    const navButton =
        'flex h-7 w-7 cursor-pointer items-center justify-center rounded-md border border-line text-text-soft hover:border-gold hover:text-gold'

    return (
        <div className="rounded-xl border border-line bg-panel p-5">
            <div className="mb-3 flex items-center justify-between">
                <h3 className="m-0 text-base">
                    {view.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
                </h3>
                <div className="flex gap-1">
                    <button
                        type="button"
                        aria-label="Previous month"
                        className={navButton}
                        onClick={() => setView(new Date(year, month - 1, 1))}
                    >
                        ‹
                    </button>
                    <button
                        type="button"
                        aria-label="Next month"
                        className={navButton}
                        onClick={() => setView(new Date(year, month + 1, 1))}
                    >
                        ›
                    </button>
                </div>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center">
                {WEEKDAYS.map((w) => (
                    <span key={w} className="py-1 font-mono text-[0.7rem] text-text-faint">
                        {w}
                    </span>
                ))}
                {cells.map((date, i) => {
                    if (!date) return <span key={`blank-${i}`} />
                    const key = dayKey(date)
                    const hasEvents = eventDays.has(key)
                    const isSelected = key === selectedKey
                    const isToday = key === todayKey
                    return (
                        <button
                            key={key}
                            type="button"
                            disabled={!hasEvents}
                            onClick={() => onSelectDay(isSelected ? null : key)}
                            className={[
                                'relative flex aspect-square items-center justify-center rounded-md text-[0.85rem]',
                                hasEvents
                                    ? 'cursor-pointer hover:bg-bg-raised'
                                    : 'cursor-default text-text-faint',
                                isSelected
                                    ? 'bg-gold/15 text-gold'
                                    : isToday
                                      ? 'font-semibold text-gold'
                                      : '',
                            ]
                                .filter(Boolean)
                                .join(' ')}
                        >
                            {date.getDate()}
                            {hasEvents && (
                                <span className="absolute bottom-[0.28rem] h-[5px] w-[5px] rounded-full bg-gold" />
                            )}
                        </button>
                    )
                })}
            </div>
            {selectedKey && (
                <button
                    type="button"
                    onClick={() => onSelectDay(null)}
                    className="mt-3 cursor-pointer font-mono text-[0.75rem] text-text-soft hover:text-gold"
                >
                    ← Show all events
                </button>
            )}
        </div>
    )
}
