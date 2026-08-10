package com.socialcoding.events.routes

import com.socialcoding.events.getAttendanceSummary
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Retrieve attendance summary.
 *
 * GET /api/events/analytics
 */
val ANALYTICS: suspend RoutingContext.() -> Unit = {
    requireRole()

    call.respond(getAttendanceSummary())
}
