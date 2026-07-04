package com.socialcoding.board

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.ProjectStatus
import com.socialcoding.projects.Projects
import com.socialcoding.db.Role
import com.socialcoding.db.Users
import com.socialcoding.db.toUser
import com.socialcoding.projects.pendingProjects
import com.socialcoding.projects.syncMilestonesToAllProjects
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
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

            // GET /api/board/settings
            // the board-wide presentation dates
            get("/settings") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                call.respond(BoardSettings.presentationDates())
            }

            // PUT /api/board/settings
            // set the presentation dates inherited by project milestones
            put("/settings") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                BoardSettings.setPresentationDates(call.receive())
                call.respond(BoardSettings.presentationDates())
            }

            // GET /api/board/members
            // every registered user, so the board can manage who holds the board role
            get("/members") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val users = transaction {
                    Users.selectAll().orderBy(Users.name).map { it.toUser() }
                }
                call.respond(users)
            }

            /**
             * A request to change a user's role.
             *
             * @param role The role to grant the user.
             */
            @Serializable data class RoleRequest(val role: Role)

            // PUT /api/board/members/{id}/role
            // promote a member to the board or demote a board member back to member
            put("/members/{id}/role") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val targetID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("user")
                // Guard against locking yourself out of the board.
                if (targetID == currentUserID()) throw InvalidAuthorization()

                val role = call.receive<RoleRequest>().role
                val updated = transaction {
                    Users.update({ Users.id eq targetID }) { it[Users.role] = role }
                }
                if (updated == 0) throw NotFound("user")

                val user = transaction {
                    Users.selectAll().where { Users.id eq targetID }.single().toUser()
                }
                call.respond(user)
            }

            /**
             * A request to rename a board member's role.
             *
             * @param title The display name for the role, or null to fall back to "Board".
             */
            @Serializable data class TitleRequest(val title: String? = null)

            // PUT /api/board/members/{id}/title
            // rename the role a board member holds (e.g. "President", "Treasurer")
            put("/members/{id}/title") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val targetID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("user")
                val title = call.receive<TitleRequest>().title?.trim()?.take(64)?.ifBlank { null }

                val updated = transaction {
                    Users.update({ Users.id eq targetID }) { it[Users.title] = title }
                }
                if (updated == 0) throw NotFound("user")

                val user = transaction {
                    Users.selectAll().where { Users.id eq targetID }.single().toUser()
                }
                call.respond(user)
            }

            /**
             * The outcome of a milestone sync.
             *
             * @param projects How many projects had their milestones refreshed.
             */
            @Serializable data class SyncResult(val projects: Int)

            // POST /api/board/projects/sync-milestones
            // re-add the MVP/Final milestones to every project with the current presentation dates
            post("/projects/sync-milestones") {
                if (currentRole() != Role.BOARD) throw InvalidAuthorization()

                val count = syncMilestonesToAllProjects(BoardSettings.presentationDates())
                call.respond(SyncResult(count))
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

                val projectID = call.parameters["id"]?.toUuidOrNull()
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
