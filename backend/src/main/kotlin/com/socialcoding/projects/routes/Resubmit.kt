package com.socialcoding.projects.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.Projects
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** POST /api/projects/{id}/resubmit — send a rejected design doc back to the board. */
val RESUBMIT: suspend RoutingContext.() -> Unit = handler@{
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    if (!detail.canManageTeam) throw InvalidAuthorization()
    if (detail.project.status != ProjectStatus.REJECTED) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Only rejected projects can be resubmitted"),
        )
    }

    transaction {
        Projects.update({ Projects.id eq projectID }) {
            it[status] = ProjectStatus.PENDING
            it[reviewNote] = null
            it[reviewedBy] = null
            it[submittedAt] = System.currentTimeMillis()
        }
    }

    call.respond(projectDetail(projectID, currentUserID(), currentRole())!!)
}
