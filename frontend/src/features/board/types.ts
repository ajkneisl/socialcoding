import type { PresentationDates } from '../projects/types'

/** Everything the board configures from its dashboard. */
export interface BoardConfig {
    presentationDates: PresentationDates
    /** Discord channel id events are announced to; blank turns announcing off. */
    announcementChannelID: string
    /** The semester design docs are filed under, e.g. "Fall 2026". */
    currentSemester: string
    /** The meeting line in the site footer; blank keeps the client's built-in default. */
    footerText: string
}
