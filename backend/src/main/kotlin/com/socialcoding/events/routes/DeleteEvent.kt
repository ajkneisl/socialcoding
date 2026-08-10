package com.socialcoding.events.routes

import com.socialcoding.api.db.query
import com.socialcoding.common.NotFound
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.EventOccurrences
import com.socialcoding.events.Events
import com.socialcoding.events.getEventByID
import com.socialcoding.user.argument
import com.socialcoding.user.requireRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

/**
 * Remove an event.
 *
 * DELETE /api/events/{id}
 */
val DELETE_EVENT: suspend RoutingContext.() -> Unit = {
    requireRole()

    val eventID = argument("id", String::toLong)
    if (getEventByID(eventID) == null) throw NotFound("event")

    query {
        EventAttendance.deleteWhere { EventAttendance.eventID eq eventID }
        EventOccurrences.deleteWhere { EventOccurrences.eventID eq eventID }
        Events.deleteWhere { Events.id eq eventID }
    }

    call.respond(HttpStatusCode.OK)
}
