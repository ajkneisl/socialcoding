package com.socialcoding.projects.routes

import com.socialcoding.user.currentRole
import com.socialcoding.user.currentUserID
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.people.Users
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.Projects
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.toIdJson
import com.socialcoding.projects.toUserIdList
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A request to replace a project's team.
 *
 * @param memberIds The IDs of the new team members.
 * @param teamLeadId The ID of the new team lead.
 */
@Serializable
private data class UpdateMembersRequest(val memberIds: List<String>, val teamLeadId: String)

/** PUT /api/projects/{id}/members — replace a project's membership and team lead. */
val UPDATE_MEMBERS: suspend RoutingContext.() -> Unit = handler@{
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    if (!detail.canManageTeam) throw InvalidAuthorization()

    val body = call.receive<UpdateMembersRequest>()

    val applied = transaction {
        val leadID = body.teamLeadId.toUuidOrNull() ?: return@transaction false
        val requestedIds = (body.memberIds.mapNotNull { it.toUuidOrNull() } + leadID).distinct()
        val teamIds =
            Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
        if (leadID !in teamIds) return@transaction false
        val ownerID =
            Projects.selectAll().where { Projects.id eq projectID }.first()[Projects.ownerId]
        val existing =
            ProjectMembers.selectAll()
                .where { ProjectMembers.projectID eq projectID }
                .associate { it[ProjectMembers.userID] to it[ProjectMembers.status] }
        Projects.update({ Projects.id eq projectID }) { it[teamLeadId] = leadID }
        // Drop anyone no longer on the team, keeping the rest at their current invite state.
        ProjectMembers.deleteWhere {
            (ProjectMembers.projectID eq projectID) and (ProjectMembers.userID notInList teamIds)
        }
        teamIds.forEach { memberID ->
            // The owner and team lead are always on the team; newly added members are
            // invited and existing members keep whatever state they already had.
            val desired =
                if (memberID == ownerID || memberID == leadID) MemberStatus.ACCEPTED
                else existing[memberID] ?: MemberStatus.PENDING
            if (memberID in existing) {
                if (existing[memberID] != desired) {
                    ProjectMembers.update({
                        (ProjectMembers.projectID eq projectID) and
                            (ProjectMembers.userID eq memberID)
                    }) {
                        it[status] = desired
                    }
                }
            } else {
                ProjectMembers.insert {
                    it[ProjectMembers.projectID] = projectID
                    it[ProjectMembers.userID] = memberID
                    it[status] = desired
                }
            }
        }
        // Drop removed members from task assignments.
        val teamSet = teamIds.toSet()
        ProjectTasks.selectAll()
            .where { ProjectTasks.projectID eq projectID }
            .map { it[ProjectTasks.id] to it[ProjectTasks.assigneeIDs].toUserIdList() }
            .forEach { (taskID, assignees) ->
                val kept = assignees.filter { it in teamSet }
                if (kept.size != assignees.size) {
                    ProjectTasks.update({ ProjectTasks.id eq taskID }) {
                        it[assigneeIDs] = kept.toIdJson()
                    }
                }
            }
        true
    }

    if (!applied) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Team lead must be a valid user"),
        )
    }

    // A lead who hands off and leaves the team may no longer be able to see a pending doc.
    val updated = projectDetail(projectID, currentUserID(), currentRole())
    if (updated == null) call.respond(HttpStatusCode.OK) else call.respond(updated)
}
