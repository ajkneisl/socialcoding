package com.socialcoding.projects

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserId
import com.socialcoding.common.ApiError
import com.socialcoding.db.ProjectMembers
import com.socialcoding.db.ProjectTasks
import com.socialcoding.db.Projects
import com.socialcoding.db.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

@Serializable
data class CreateProjectRequest(
    val title: String,
    val description: String,
    val repoUrl: String? = null,
    val teamLeadId: Long? = null,
    val memberIds: List<Long> = emptyList(),
    val designDoc: DesignDocContent = DesignDocContent(),
    val tasks: List<TaskInput> = emptyList(),
)

@Serializable
data class UpdateDesignRequest(
    val title: String,
    val description: String,
    val repoUrl: String? = null,
    val designDoc: DesignDocContent,
)

@Serializable data class UpdateMembersRequest(val memberIds: List<Long>, val teamLeadId: Long)

@Serializable data class UpdateTasksRequest(val tasks: List<TaskInput>)

/** Public project listing plus the authenticated design doc lifecycle. */
fun Route.projectRoutes() {
    get("/projects") {
        val projects = transaction {
            projectsWithOwners()
                .where { Projects.status eq ProjectStatus.APPROVED }
                .orderBy(Projects.submittedAt)
                .map { it.toProject(it[Users.name]) }
        }
        call.respond(projects)
    }

    authenticate("session") {
        post("/projects") {
            val userId = currentUserId()
            val body = call.receive<CreateProjectRequest>()
            if (body.title.isBlank() || body.description.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Title and description are required"),
                )
                return@post
            }
            val projectId = transaction {
                val requestedIds =
                    (body.memberIds + userId + listOfNotNull(body.teamLeadId)).distinct()
                val teamIds =
                    Users.selectAll()
                        .where { Users.id inList requestedIds }
                        .map { it[Users.id] }
                val leadId = body.teamLeadId?.takeIf { it in teamIds } ?: userId
                val id =
                    Projects.insert {
                        it[title] = body.title.trim()
                        it[description] = body.description.trim()
                        it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                        it[ownerId] = userId
                        it[teamLeadId] = leadId
                        it[designDoc] = encodeDesignDoc(body.designDoc)
                        it[status] = ProjectStatus.PENDING
                        it[submittedAt] = System.currentTimeMillis()
                    } get Projects.id
                teamIds.forEach { memberId ->
                    ProjectMembers.insert {
                        it[ProjectMembers.projectId] = id
                        it[ProjectMembers.userId] = memberId
                    }
                }
                replaceTasks(id, withRequiredMilestones(body.tasks), teamIds.toSet())
                id
            }
            call.respond(HttpStatusCode.Created, loadDetail(projectId, userId, currentRole())!!)
        }

        get("/projects/mine") {
            val userId = currentUserId()
            val projects = transaction {
                val memberOf =
                    ProjectMembers.selectAll()
                        .where { ProjectMembers.userId eq userId }
                        .map { it[ProjectMembers.projectId] }
                projectsWithOwners()
                    .where {
                        (Projects.ownerId eq userId) or
                            (Projects.teamLeadId eq userId) or
                            (Projects.id inList memberOf)
                    }
                    .orderBy(Projects.submittedAt)
                    .map { it.toProject(it[Users.name]) }
            }
            call.respond(projects)
        }

        get("/projects/{id}") {
            val projectId = call.parameters["id"]?.toLongOrNull()
            val detail = projectId?.let { loadDetail(it, currentUserId(), currentRole()) }
            if (detail == null)
                call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            else call.respond(detail)
        }

        put("/projects/{id}/design") {
            val projectId = call.parameters["id"]?.toLongOrNull()
            val userId = currentUserId()
            val detail = projectId?.let { loadDetail(it, userId, currentRole()) }
            if (projectId == null || detail == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                return@put
            }
            if (!detail.canEdit) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Only the team can edit the design doc"),
                )
                return@put
            }
            val body = call.receive<UpdateDesignRequest>()
            if (body.title.isBlank() || body.description.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Title and description are required"),
                )
                return@put
            }
            transaction {
                Projects.update({ Projects.id eq projectId }) {
                    it[title] = body.title.trim()
                    it[description] = body.description.trim()
                    it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                    it[designDoc] = encodeDesignDoc(body.designDoc)
                }
            }
            call.respond(loadDetail(projectId, userId, currentRole())!!)
        }

        put("/projects/{id}/members") {
            val projectId = call.parameters["id"]?.toLongOrNull()
            val userId = currentUserId()
            val detail = projectId?.let { loadDetail(it, userId, currentRole()) }
            if (projectId == null || detail == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                return@put
            }
            if (!detail.canManageTeam) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Only the team lead can modify the team"),
                )
                return@put
            }
            val body = call.receive<UpdateMembersRequest>()
            val applied = transaction {
                val requestedIds = (body.memberIds + body.teamLeadId).distinct()
                val teamIds =
                    Users.selectAll()
                        .where { Users.id inList requestedIds }
                        .map { it[Users.id] }
                if (body.teamLeadId !in teamIds) {
                    return@transaction false
                }
                Projects.update({ Projects.id eq projectId }) {
                    it[teamLeadId] = body.teamLeadId
                }
                ProjectMembers.deleteWhere { ProjectMembers.projectId eq projectId }
                teamIds.forEach { memberId ->
                    ProjectMembers.insert {
                        it[ProjectMembers.projectId] = projectId
                        it[ProjectMembers.userId] = memberId
                    }
                }
                // Drop removed members from task assignments.
                val teamSet = teamIds.toSet()
                ProjectTasks.selectAll()
                    .where { ProjectTasks.projectId eq projectId }
                    .map { it[ProjectTasks.id] to it[ProjectTasks.assigneeIds].toIdList() }
                    .forEach { (taskId, assignees) ->
                        val kept = assignees.filter { it in teamSet }
                        if (kept.size != assignees.size) {
                            ProjectTasks.update({ ProjectTasks.id eq taskId }) {
                                it[assigneeIds] = kept.joinToString(",")
                            }
                        }
                    }
                true
            }
            if (!applied) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Team lead must be a valid user"),
                )
                return@put
            }
            // A lead who hands off and leaves the team may no longer be able to see a pending
            // doc.
            val updatedDetail = loadDetail(projectId, userId, currentRole())
            if (updatedDetail == null) call.respond(HttpStatusCode.OK)
            else call.respond(updatedDetail)
        }

        put("/projects/{id}/tasks") {
            val projectId = call.parameters["id"]?.toLongOrNull()
            val userId = currentUserId()
            val detail = projectId?.let { loadDetail(it, userId, currentRole()) }
            if (projectId == null || detail == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                return@put
            }
            if (!detail.canEdit) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Only the team can edit the deliverables"),
                )
                return@put
            }
            val body = call.receive<UpdateTasksRequest>()
            transaction {
                val teamIds = memberIdsOf(projectId).toSet()
                replaceTasks(projectId, withRequiredMilestones(body.tasks), teamIds)
            }
            call.respond(loadDetail(projectId, userId, currentRole())!!)
        }
    }
}
