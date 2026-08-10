package com.socialcoding.events.routes

import com.socialcoding.api.db.query
import com.socialcoding.common.NotFound
import com.socialcoding.common.invalidArguments
import com.socialcoding.events.EventAnnouncements
import com.socialcoding.events.Events
import com.socialcoding.events.getEventByID
import com.socialcoding.events.models.EventRequest
import com.socialcoding.user.argument
import com.socialcoding.user.body
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Update an event.
 *
 * PUT /api/events/{id}
 */
val UPDATE_EVENT: suspend RoutingContext.() -> Unit = handler@{
    requireRole()

    val eventID = argument("id", String::toLong)
    if (getEventByID(eventID) == null) throw NotFound("event")

    val body = body<EventRequest>()

    when {
        body.title.isBlank() ->
            throw invalidArguments("title" to "Title must not be blank.")

        body.summary.isBlank() ->
            throw invalidArguments("summary" to "Summary must not be blank.")
    }

    query {
        Events.update({ Events.id eq eventID }) {
            it[title] = body.title.trim().take(200)
            it[summary] = body.summary.trim()
            it[Events.body] = body.body.trim()
            it[startsAt] = body.startsAt
            it[location] = body.location?.trim()?.ifBlank { null }
            it[burrowUrl] = body.burrowUrl?.trim()?.ifBlank { null }
            it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
            it[attendance] = body.attendance
            it[recurring] = body.recurring
            it[announce] = body.announce
        }
    }

    // Same as on create: the sweep owns delivery unless this event's noon has already passed.
    EventAnnouncements.announceIfDue(getEventByID(eventID)!!)

    call.respond(getEventByID(eventID)!!)
}
