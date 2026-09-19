package com.socialcoding.events.routes

import com.socialcoding.events.getAllEvents
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Retrieve all public events.
 *
 * GET /api/events
 */
val LIST_EVENTS: suspend RoutingContext.() -> Unit = {
    call.respond(getAllEvents())
}
