package com.socialcoding.board.routes

import com.socialcoding.auth.argument
import com.socialcoding.auth.body
import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.board.models.BoardDecision
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import com.socialcoding.db.exists
import com.socialcoding.db.query
import com.socialcoding.discord.Discord
import com.socialcoding.projects.Projects
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.toUuid
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A request to finalize a review.
 *
 * @param note An optional note for the project.
 */
@Serializable private data class ReviewRequest(val note: String? = null)

/** POST /api/board/projects/{id}/{decision} — finalize a decision on a project. */
val DECIDE: suspend RoutingContext.() -> Unit = {
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    val reviewerID = currentUserID()
    val projectID = argument("id", String::toUuid)
    val decision = argument("decision") { BoardDecision.valueOf(uppercase()) }
    val note = body<ReviewRequest>().note

    if (!Projects.exists(projectID)) throw NotFound("project")

    query {
        Projects.update({ Projects.id eq projectID }) {
            when (decision) {
                BoardDecision.APPROVE -> {
                    it[status] = ProjectStatus.APPROVED
                    it[reviewedBy] = reviewerID
                    it[reviewNote] = note
                }

                BoardDecision.REJECT -> {
                    it[status] = ProjectStatus.REJECTED
                    it[reviewedBy] = reviewerID
                    it[reviewNote] = note
                }

                BoardDecision.ACTIVATE -> it[active] = true
                BoardDecision.DEACTIVATE -> it[active] = false
            }
        }
    }

    when (decision) {
        BoardDecision.APPROVE -> Discord.onProjectApproved(projectID)
        BoardDecision.ACTIVATE -> Discord.onProjectActivated(projectID)
        BoardDecision.DEACTIVATE -> Discord.onProjectRetired(projectID)
        BoardDecision.REJECT -> {}
    }

    call.respond(HttpStatusCode.OK)
}
