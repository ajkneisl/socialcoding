package com.socialcoding.events.routes

import com.socialcoding.auth.currentUserID
import com.socialcoding.common.APIError
import com.socialcoding.common.NotFound
import com.socialcoding.events.ATTENDANCE_CLOSES_MS
import com.socialcoding.events.ATTENDANCE_OPENS_MS
import com.socialcoding.events.attendeeCount
import com.socialcoding.events.eventById
import com.socialcoding.events.recordAttendance
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * The result of a check-in.
 *
 * @param status "recorded" for a new check-in, "already" if previously counted.
 * @param attendees The event's total attendee count after the check-in.
 */
@Serializable private data class AttendResponse(val status: String, val attendees: Long)

/** POST /api/events/{id}/attend — the signed-in user checks in, within the attendance window. */
val ATTEND: suspend RoutingContext.() -> Unit = handler@{
    val userID = currentUserID()
    val eventID = call.parameters["id"]?.toLongOrNull() ?: throw NotFound("event")
    val event = eventById(eventID) ?: throw NotFound("event")

    if (!event.attendance) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Attendance isn't enabled for this event."),
        )
    }

    val now = System.currentTimeMillis()
    if (now < event.startsAt + ATTENDANCE_OPENS_MS) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Check-in opens an hour before the event starts."),
        )
    }
    if (now > event.startsAt + ATTENDANCE_CLOSES_MS) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Check-in has closed for this event."),
        )
    }

    val outcome = recordAttendance(eventID, userID)
    call.respond(AttendResponse(outcome.name.lowercase(), attendeeCount(eventID)))
}
