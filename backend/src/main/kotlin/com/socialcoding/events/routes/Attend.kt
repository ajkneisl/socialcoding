package com.socialcoding.events.routes

import com.socialcoding.common.APIError
import com.socialcoding.common.NotFound
import com.socialcoding.events.ATTENDANCE_CLOSES_MS
import com.socialcoding.events.ATTENDANCE_OPENS_MS
import com.socialcoding.events.AttendOutcome
import com.socialcoding.events.getAttendeeCount
import com.socialcoding.events.getEventByID
import com.socialcoding.events.recordAttendance
import com.socialcoding.user.argument
import com.socialcoding.user.currentUserID
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * The result of a check-in.
 *
 * @param status [AttendOutcome.RECORDED] for a new check-in, [AttendOutcome.ALREADY] if the
 *   member was already counted.
 * @param attendees The event's total attendee count after the check-in.
 */
@Serializable
private data class AttendResponse(
    val status: AttendOutcome,
    val attendees: Long,
)

/**
 * Check in for an event.
 *
 * POST /api/events/{id}/attend
 */
val ATTEND: suspend RoutingContext.() -> Unit = {
    val userID = currentUserID()
    val eventID = argument("id", String::toLong)
    val event = getEventByID(eventID) ?: throw NotFound("event")

    if (!event.attendance)
        throw APIError("Attendance isn't enabled for this event.")

    val now = System.currentTimeMillis()
    if (now < event.startsAt + ATTENDANCE_OPENS_MS)
        throw APIError("Check-in opens an hour before the event starts.")

    if (now > event.startsAt + ATTENDANCE_CLOSES_MS)
        throw APIError("Check-in has closed for this event.")

    val outcome = recordAttendance(eventID, userID)
    call.respond(AttendResponse(outcome, getAttendeeCount(eventID)))
}
