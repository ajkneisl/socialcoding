package com.socialcoding.board.routes

import com.socialcoding.api.Discord
import com.socialcoding.api.db.exists
import com.socialcoding.api.db.query
import com.socialcoding.common.NotFound
import com.socialcoding.projects.ProjectLikes
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.Projects
import com.socialcoding.projects.docs.DesignDocs
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.tasks.SentReminders
import com.socialcoding.projects.toUuid
import com.socialcoding.user.argument
import com.socialcoding.user.requireRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

/**
 * Remove a project along with everything filed against it: its team, hearts, deliverables and
 * design docs. Deleting is permanent — rejecting a project leaves the team able to resubmit,
 * whereas this does not.
 *
 * DELETE /api/board/projects/{id}
 */
val DELETE_PROJECT: suspend RoutingContext.() -> Unit = {
    requireRole()

    val projectID = argument("id", String::toUuid)
    if (!Projects.exists(projectID)) throw NotFound("project")

    // Archive the channel before the row goes: it holds the only pointer to the channel.
    Discord.onProjectDeleted(projectID)

    query {
        ProjectLikes.deleteWhere { ProjectLikes.projectID eq projectID }
        ProjectMembers.deleteWhere { ProjectMembers.projectID eq projectID }
        ProjectTasks.deleteWhere { ProjectTasks.projectID eq projectID }
        SentReminders.deleteWhere { SentReminders.projectID eq projectID }
        DesignDocs.deleteWhere { DesignDocs.projectID eq projectID }
        Projects.deleteWhere { Projects.id eq projectID }
    }

    call.respond(HttpStatusCode.OK)
}
