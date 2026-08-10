package com.socialcoding.projects.routes

import com.socialcoding.user.currentUserID
import com.socialcoding.common.NotFound
import com.socialcoding.api.db.query
import com.socialcoding.api.db.toEntity
import com.socialcoding.api.db.update
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.ProjectMembership
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** POST /api/projects/{id}/invite/accept — accept a pending invite, joining the team. */
val ACCEPT_INVITE: suspend RoutingContext.() -> Unit = {
    val userID = currentUserID()
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val invite =
        query {
            ProjectMembers.selectAll()
                .where {
                    (ProjectMembers.projectID eq projectID) and
                        (ProjectMembers.userID eq userID) and
                        (ProjectMembers.status eq MemberStatus.PENDING)
                }
                .firstOrNull()
                ?.toEntity<ProjectMembership>()
        } ?: throw NotFound("invite")

    invite.update(ProjectMembership::status, MemberStatus.ACCEPTED).join()
    call.respond(HttpStatusCode.OK)
}
