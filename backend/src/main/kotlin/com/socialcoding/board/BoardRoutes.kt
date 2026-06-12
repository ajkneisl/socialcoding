package com.socialcoding.board

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserId
import com.socialcoding.common.ApiError
import com.socialcoding.db.Projects
import com.socialcoding.db.Users
import com.socialcoding.people.Role
import com.socialcoding.projects.ProjectStatus
import com.socialcoding.projects.projectsWithOwners
import com.socialcoding.projects.toProject
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

@Serializable data class ReviewRequest(val note: String? = null)

/** Board-only review queue and project activation. */
fun Route.boardRoutes() {
    authenticate("session") {
        route("/board") {
            get("/projects") {
                if (currentRole() != Role.BOARD) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
                    return@get
                }
                val pending = transaction {
                    projectsWithOwners()
                        .where { Projects.status eq ProjectStatus.PENDING }
                        .orderBy(Projects.submittedAt)
                        .map { it.toProject(it[Users.name]) }
                }
                call.respond(pending)
            }

            post("/projects/{id}/{decision}") {
                if (currentRole() != Role.BOARD) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
                    return@post
                }
                val projectId = call.parameters["id"]?.toLongOrNull()
                val decision = call.parameters["decision"]
                if (
                    projectId == null ||
                        decision !in setOf("approve", "reject", "activate", "deactivate")
                ) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Unknown project or decision"),
                    )
                    return@post
                }
                val note = runCatching { call.receive<ReviewRequest>().note }.getOrNull()
                val reviewerId = currentUserId()
                val updated = transaction {
                    Projects.update({ Projects.id eq projectId }) {
                        when (decision) {
                            "approve" -> {
                                it[status] = ProjectStatus.APPROVED
                                it[reviewedBy] = reviewerId
                                it[reviewNote] = note
                            }
                            "reject" -> {
                                it[status] = ProjectStatus.REJECTED
                                it[reviewedBy] = reviewerId
                                it[reviewNote] = note
                            }
                            "activate" -> it[active] = true
                            "deactivate" -> it[active] = false
                        }
                    }
                }
                if (updated == 0)
                    call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                else call.respond(HttpStatusCode.OK)
            }
        }
    }
}
