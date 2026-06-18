/** Local day identifier (year-month-day) used to match events to calendar cells. */
export function dayKey(date: Date) {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}
