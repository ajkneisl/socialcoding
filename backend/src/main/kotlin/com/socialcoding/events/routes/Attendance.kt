package com.socialcoding.events.routes

import com.socialcoding.events.getEventAttendees
import com.socialcoding.user.argument
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Retrieve attendance information about a route.
 *
 * GET /api/events/{id}/attendance
 */
val ATTENDANCE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val eventID = argument("id", String::toLong)
    call.respond(getEventAttendees(eventID))
}
