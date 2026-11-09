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

/** An employer as the board manages it. */
export interface Employer {
    key: string
    name: string
    /** Whether it appears on the public home page. */
    visible: boolean
    /** How many people on the public People page list it. */
    memberCount: number
}

/** The profile details the board can edit on a member's behalf. */
export interface MemberProfile {
    gradYear: number | null
    github: string | null
    linkedin: string | null
    website: string | null
    company: string | null
    listed: boolean
}
