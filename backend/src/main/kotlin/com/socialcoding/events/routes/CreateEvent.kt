package com.socialcoding.events.routes

import com.socialcoding.api.db.query
import com.socialcoding.common.arguments
import com.socialcoding.events.EventAnnouncements
import com.socialcoding.events.Events
import com.socialcoding.events.getEventByID
import com.socialcoding.events.models.EventRequest
import com.socialcoding.user.body
import com.socialcoding.user.currentUserID
import com.socialcoding.user.requireRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Create a new event.
 *
 * POST /api/events
 */
val CREATE_EVENT: suspend RoutingContext.() -> Unit = handler@{
    requireRole()

    val body = body<EventRequest>()
    arguments {
        when {
            body.title.isBlank() -> put("title", "Title may not be blank")
            body.summary.isBlank() -> put("summary", "Summary may not be blank")
        }
    }

    val userID = currentUserID()
    val id = query {
        Events.insert {
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
            it[createdBy] = userID
            it[createdAt] = System.currentTimeMillis()
        } get Events.id
    }

    EventAnnouncements.announceIfDue(getEventByID(id)!!)

    call.respond(HttpStatusCode.Created, getEventByID(id)!!)
}
