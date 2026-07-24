package com.socialcoding.events.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.db.Role
import com.socialcoding.events.attendanceSummary
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/events/analytics — board members read per-event attendance totals. */
val ANALYTICS: suspend RoutingContext.() -> Unit = {
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    call.respond(attendanceSummary())
}
