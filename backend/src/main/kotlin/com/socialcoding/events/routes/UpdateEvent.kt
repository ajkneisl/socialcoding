package com.socialcoding.events.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import com.socialcoding.events.Events
import com.socialcoding.events.eventById
import com.socialcoding.events.models.EventRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** PUT /api/events/{id} — any board member may edit any event. */
val UPDATE_EVENT: suspend RoutingContext.() -> Unit = handler@{
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    val eventID = call.parameters["id"]?.toLongOrNull() ?: throw NotFound("event")
    val body = call.receive<EventRequest>()
    if (body.title.isBlank() || body.summary.isBlank()) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Title and summary are required"),
        )
    }

    val updated = transaction {
        Events.update({ Events.id eq eventID }) {
            it[title] = body.title.trim().take(200)
            it[summary] = body.summary.trim()
            it[Events.body] = body.body.trim()
            it[startsAt] = body.startsAt
            it[location] = body.location?.trim()?.ifBlank { null }
            it[burrowUrl] = body.burrowUrl?.trim()?.ifBlank { null }
            it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
            it[attendance] = body.attendance
        }
    }
    if (updated == 0) throw NotFound("event")

    call.respond(eventById(eventID)!!)
}
