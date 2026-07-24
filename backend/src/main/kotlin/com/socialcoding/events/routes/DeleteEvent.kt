package com.socialcoding.events.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.Events
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** DELETE /api/events/{id} — any board member may remove any event. */
val DELETE_EVENT: suspend RoutingContext.() -> Unit = {
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    val eventID = call.parameters["id"]?.toLongOrNull() ?: throw NotFound("event")
    val deleted =
        transaction {
            // Clear check-ins first; they reference the event via a foreign key.
            EventAttendance.deleteWhere { EventAttendance.eventID eq eventID }
            Events.deleteWhere { Events.id eq eventID }
        }
    if (deleted == 0) throw NotFound("event")

    call.respond(HttpStatusCode.OK)
}
