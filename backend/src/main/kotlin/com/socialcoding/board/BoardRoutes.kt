package com.socialcoding.board

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.ProjectStatus
import com.socialcoding.projects.Projects
import com.socialcoding.db.Role
import com.socialcoding.projects.pendingProjects
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Board-only review queue and project activation. */
fun Route.boardRoutes() {
    authenticate("session") {
        route("/board") {
            // GET /api/board/projects
            // view all pending projects
            get("/projects") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                call.respond(pendingProjects())
            }

            /**
             * A request to finalize a review.
             *
             * @param note An optional note for the project.
             */
            @Serializable data class ReviewRequest(val note: String? = null)
            val decisions = setOf("approve", "reject", "activate", "deactivate")

            // POST /api/board/projects/{id}/{decision}
            // finalize a decision on a project.
            post("/projects/{id}/{decision}") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val projectID = call.parameters["id"]?.toLongOrNull()
                val decision = call.parameters["decision"]?.lowercase()

                if (projectID == null || decision !in decisions) throw NotFound("project")

                val note = runCatching { call.receive<ReviewRequest>().note }.getOrNull()
                val reviewerID = currentUserID()

                val updated = transaction {
                    Projects.update({ Projects.id eq projectID }) {
                        when (decision) {
                            "approve" -> {
                                it[status] = ProjectStatus.APPROVED
                                it[reviewedBy] = reviewerID
                                it[reviewNote] = note
                            }

                            "reject" -> {
                                it[status] = ProjectStatus.REJECTED
                                it[reviewedBy] = reviewerID
                                it[reviewNote] = note
                            }

                            "activate" -> it[active] = true
                            "deactivate" -> it[active] = false
                        }
                    }
                }

                if (updated == 0) throw NotFound("project")

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
