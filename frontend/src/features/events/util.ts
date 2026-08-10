/** Local day identifier (year-month-day) used to match events to calendar cells. */
export function dayKey(date: Date) {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

/** Check-in closes 2 hours after an event starts (mirrors the backend attendance window). */
export const ATTENDANCE_CLOSES_MS = 2 * 60 * 60 * 1000

/** Whether the check-in period for an event has already ended. */
export function checkInClosed(startsAt: number) {
    return Date.now() > startsAt + ATTENDANCE_CLOSES_MS
}

/** Weekday names in `Date.getDay()` order, for the recurring-event day picker. */
export const WEEKDAYS = [
    'Sunday',
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
]

/** A date's local time of day as the "HH:mm" string a time input expects. */
export function toTimeInput(ms: number) {
    const d = new Date(ms)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * Epoch ms of the next time `weekday` (0 = Sunday) comes around at `time` ("HH:mm"). Today counts
 * when that moment is still ahead, so a recurring event created the morning of its meeting starts
 * that same day rather than a week out.
 */
export function nextWeekday(weekday: number, time: string, from = new Date()) {
    const [hours, minutes] = time.split(':').map(Number)
    const next = new Date(from)
    next.setHours(hours, minutes, 0, 0)

    let daysAhead = (weekday - next.getDay() + 7) % 7
    if (daysAhead === 0 && next.getTime() <= from.getTime()) daysAhead = 7
    next.setDate(next.getDate() + daysAhead)

    return next.getTime()
}
