package com.socialcoding.board

import java.time.Instant
import java.time.ZoneId

/**
 * The label for the academic term [epochMillis] falls in, formatted `"<Term> <year>"` — `"Fall
 * 2026"`.
 *
 * Terms follow the University calendar rather than anything configurable: January through May is
 * Spring, June and July are Summer, and August through December is Fall. Deriving the label means
 * design docs file themselves under the right semester without the board having to remember to roll
 * a setting over; [BoardSettings.CURRENT_SEMESTER] is there for the semesters that don't line up.
 */
fun semesterLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val term =
        when (date.monthValue) {
            in 1..5 -> "Spring"
            in 6..7 -> "Summer"
            else -> "Fall"
        }
    return "$term ${date.year}"
}
