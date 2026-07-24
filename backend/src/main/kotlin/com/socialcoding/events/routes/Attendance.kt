package com.socialcoding.events.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import com.socialcoding.events.attendeesOf
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/events/{id}/attendance — board members read an event's attendee list (for export). */
val ATTENDANCE: suspend RoutingContext.() -> Unit = {
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    val eventID = call.parameters["id"]?.toLongOrNull() ?: throw NotFound("event")
    call.respond(attendeesOf(eventID))
}
