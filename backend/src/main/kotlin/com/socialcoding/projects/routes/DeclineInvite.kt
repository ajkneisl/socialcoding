package com.socialcoding.projects.routes

import com.socialcoding.user.currentUserID
import com.socialcoding.common.NotFound
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** POST /api/projects/{id}/invite/decline — decline a pending invite, dropping the user. */
val DECLINE_INVITE: suspend RoutingContext.() -> Unit = {
    val userID = currentUserID()
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val removed = transaction {
        ProjectMembers.deleteWhere {
            (ProjectMembers.projectID eq projectID) and
                (ProjectMembers.userID eq userID) and
                (ProjectMembers.status eq MemberStatus.PENDING)
        }
    }
    if (removed == 0) throw NotFound("invite")
    call.respond(HttpStatusCode.OK)
}
