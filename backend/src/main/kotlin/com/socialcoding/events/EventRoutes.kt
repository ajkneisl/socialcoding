package com.socialcoding.events

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.ApiError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Public event listing plus board-only creation and removal. */
fun Route.eventRoutes() {
    // GET /api/events
    // list every event for the public Events page and its calendar
    get("/events") { call.respond(listEvents()) }

    authenticate("session") {
        route("/events") {
            /**
             * A request to publish a new event.
             *
             * @param title The event title.
             * @param summary A short blurb shown in the list.
             * @param body The full write-up revealed by "Read more".
             * @param startsAt When the event takes place, in epoch ms.
             * @param location The optional location.
             * @param burrowUrl The optional external Burrow link.
             * @param imageUrl The optional promotional image.
             */
            @Serializable
            data class CreateEventRequest(
                val title: String,
                val summary: String,
                val body: String = "",
                val startsAt: Long,
                val location: String? = null,
                val burrowUrl: String? = null,
                val imageUrl: String? = null,
            )

            // POST /api/events
            // board members publish a new event
            post {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val body = call.receive<CreateEventRequest>()
                if (body.title.isBlank() || body.summary.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Title and summary are required"),
                    )
                }

                val userID = currentUserID()
                val id = transaction {
                    Events.insert {
                        it[title] = body.title.trim().take(200)
                        it[summary] = body.summary.trim()
                        it[Events.body] = body.body.trim()
                        it[startsAt] = body.startsAt
                        it[location] = body.location?.trim()?.ifBlank { null }
                        it[burrowUrl] = body.burrowUrl?.trim()?.ifBlank { null }
                        it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
                        it[createdBy] = userID
                        it[createdAt] = System.currentTimeMillis()
                    } get Events.id
                }

                call.respond(HttpStatusCode.Created, eventById(id)!!)
            }

            // DELETE /api/events/{id}
            // board members remove an event
            delete("/{id}") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val eventID = call.parameters["id"]?.toLongOrNull() ?: throw NotFound("event")
                val deleted = transaction { Events.deleteWhere { Events.id eq eventID } }
                if (deleted == 0) throw NotFound("event")

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
