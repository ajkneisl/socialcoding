package com.socialcoding.projects.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.TaskInput
import com.socialcoding.projects.memberIdsOf
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.replaceTasks
import com.socialcoding.projects.toUuidOrNull
import com.socialcoding.projects.withRequiredMilestones
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * A request to replace a project's deliverables.
 *
 * @param tasks The new list of tasks.
 */
@Serializable private data class UpdateTasksRequest(val tasks: List<TaskInput>)

/** PUT /api/projects/{id}/tasks — replace deliverables, keeping only on-team assignees. */
val UPDATE_TASKS: suspend RoutingContext.() -> Unit = {
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    if (!detail.canEdit) throw InvalidAuthorization()

    val body = call.receive<UpdateTasksRequest>()

    transaction {
        val teamIds = memberIdsOf(projectID).toSet()
        replaceTasks(
            projectID,
            withRequiredMilestones(body.tasks, BoardSettings.presentationDates()),
            teamIds,
        )
    }

    call.respond(projectDetail(projectID, currentUserID(), currentRole())!!)
}
