/** Local day identifier (year-month-day) used to match events to calendar cells. */
export function dayKey(date: Date) {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

/** Check-in opens an hour before an event starts (mirrors the backend attendance window). */
export const ATTENDANCE_OPENS_MS = -60 * 60 * 1000

/** Check-in closes 2 hours after an event starts (mirrors the backend attendance window). */
export const ATTENDANCE_CLOSES_MS = 2 * 60 * 60 * 1000

/** Whether the check-in period for an event has already ended. */
export function checkInClosed(startsAt: number) {
    return Date.now() > startsAt + ATTENDANCE_CLOSES_MS
}
