package com.socialcoding.events.routes

import com.socialcoding.events.listEvents
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/events — every event for the public Events page and its calendar. */
val LIST_EVENTS: suspend RoutingContext.() -> Unit = { call.respond(listEvents()) }
