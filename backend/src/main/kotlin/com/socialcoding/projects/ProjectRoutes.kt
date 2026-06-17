package com.socialcoding.projects

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.ApiError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.ProjectMembers
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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Public project listing plus the authenticated design doc lifecycle. */
fun Route.projectRoutes() {
    // GET /api/projects
    // list every approved project
    get("/projects") { call.respond(listApprovedProjects()) }

    authenticate("session") {
        /**
         * A request to create a new project.
         *
         * @param title The project title.
         * @param description The project description.
         * @param repoUrl The optional GitHub repository URL.
         * @param teamLeadId The ID of the team lead; defaults to the creator.
         * @param memberIds The IDs of the team members.
         * @param designDoc The design doc answers.
         * @param tasks The initial deliverables.
         */
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

        // POST /api/projects
        // submit a new project for board review
        post("/projects") {
            val userID = currentUserID()
            val body = call.receive<CreateProjectRequest>()

            if (body.title.isBlank() || body.description.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Title and description are required"),
                )
            }

            val projectID = transaction {
                val requestedIds =
                    (body.memberIds + userID + listOfNotNull(body.teamLeadId)).distinct()
                val teamIds =
                    Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
                val leadID = body.teamLeadId?.takeIf { it in teamIds } ?: userID
                val id =
                    Projects.insert {
                        it[title] = body.title.trim()
                        it[description] = body.description.trim()
                        it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                        it[ownerId] = userID
                        it[teamLeadId] = leadID
                        it[designDoc] = encodeDesignDoc(body.designDoc)
                        it[status] = ProjectStatus.PENDING
                        it[submittedAt] = System.currentTimeMillis()
                    } get Projects.id
                teamIds.forEach { memberID ->
                    ProjectMembers.insert {
                        it[ProjectMembers.projectID] = id
                        it[ProjectMembers.userID] = memberID
                    }
                }
                replaceTasks(id, withRequiredMilestones(body.tasks), teamIds.toSet())
                id
            }

            call.respond(
                HttpStatusCode.Created,
                ProjectDetail.from(projectID, userID, currentRole())!!,
            )
        }

        // GET /api/projects/mine
        // list the projects the signed-in user owns, leads, or is a member of
        get("/projects/mine") { call.respond(projectsForUser(currentUserID())) }

        // GET /api/projects/{id}
        // load a single project's full design doc
        get("/projects/{id}") {
            val projectID = call.parameters["id"]?.toLongOrNull()
            val detail =
                projectID?.let { ProjectDetail.from(it, currentUserID(), currentRole()) }
                    ?: throw NotFound("project")

            call.respond(detail)
        }

        /**
         * A request to update a project's design doc.
         *
         * @param title The project title.
         * @param description The project description.
         * @param repoUrl The optional GitHub repository URL.
         * @param designDoc The design doc answers.
         */
        @Serializable
        data class UpdateDesignRequest(
            val title: String,
            val description: String,
            val repoUrl: String? = null,
            val designDoc: DesignDocContent,
        )

        // PUT /api/projects/{id}/design
        // update a project's title, description, repo link, and design doc answers
        put("/projects/{id}/design") {
            val projectID = call.parameters["id"]?.toLongOrNull()
            val detail =
                projectID?.let { ProjectDetail.from(it, currentUserID(), currentRole()) }
                    ?: throw NotFound("project")
            if (!detail.canEdit) throw InvalidAuthorization()

            val body = call.receive<UpdateDesignRequest>()
            if (body.title.isBlank() || body.description.isBlank()) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Title and description are required"),
                )
            }

            transaction {
                Projects.update({ Projects.id eq detail.project.id }) {
                    it[title] = body.title.trim()
                    it[description] = body.description.trim()
                    it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                    it[designDoc] = encodeDesignDoc(body.designDoc)
                }
            }

            call.respond(ProjectDetail.from(detail.project.id, currentUserID(), currentRole())!!)
        }

        /**
         * A request to replace a project's team.
         *
         * @param memberIds The IDs of the new team members.
         * @param teamLeadId The ID of the new team lead.
         */
        @Serializable
        data class UpdateMembersRequest(val memberIds: List<Long>, val teamLeadId: Long)

        // PUT /api/projects/{id}/members
        // replace a project's membership and team lead
        put("/projects/{id}/members") {
            val projectID = call.parameters["id"]?.toLongOrNull()
            val detail =
                projectID?.let { ProjectDetail.from(it, currentUserID(), currentRole()) }
                    ?: throw NotFound("project")
            if (!detail.canManageTeam) throw InvalidAuthorization()

            val body = call.receive<UpdateMembersRequest>()

            val applied = transaction {
                val requestedIds = (body.memberIds + body.teamLeadId).distinct()
                val teamIds =
                    Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
                if (body.teamLeadId !in teamIds) return@transaction false
                Projects.update({ Projects.id eq detail.project.id }) {
                    it[teamLeadId] = body.teamLeadId
                }
                ProjectMembers.deleteWhere { ProjectMembers.projectID eq detail.project.id }
                teamIds.forEach { memberID ->
                    ProjectMembers.insert {
                        it[ProjectMembers.projectID] = detail.project.id
                        it[ProjectMembers.userID] = memberID
                    }
                }
                // Drop removed members from task assignments.
                val teamSet = teamIds.toSet()
                ProjectTasks.selectAll()
                    .where { ProjectTasks.projectID eq detail.project.id }
                    .map { it[ProjectTasks.id] to it[ProjectTasks.assigneeIDs].toIDList() }
                    .forEach { (taskID, assignees) ->
                        val kept = assignees.filter { it in teamSet }
                        if (kept.size != assignees.size) {
                            ProjectTasks.update({ ProjectTasks.id eq taskID }) {
                                it[assigneeIDs] = kept.joinToString(",")
                            }
                        }
                    }
                true
            }

            if (!applied) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Team lead must be a valid user"),
                )
            }

            // A lead who hands off and leaves the team may no longer be able to see a pending doc.
            val updated = ProjectDetail.from(detail.project.id, currentUserID(), currentRole())
            if (updated == null) call.respond(HttpStatusCode.OK) else call.respond(updated)
        }

        /**
         * A request to replace a project's deliverables.
         *
         * @param tasks The new list of tasks.
         */
        @Serializable data class UpdateTasksRequest(val tasks: List<TaskInput>)

        // PUT /api/projects/{id}/tasks
        // replace a project's deliverables, keeping only assignees who are on the team
        put("/projects/{id}/tasks") {
            val projectID = call.parameters["id"]?.toLongOrNull()
            val detail =
                projectID?.let { ProjectDetail.from(it, currentUserID(), currentRole()) }
                    ?: throw NotFound("project")
            if (!detail.canEdit) throw InvalidAuthorization()

            val body = call.receive<UpdateTasksRequest>()

            transaction {
                val teamIds = memberIdsOf(detail.project.id).toSet()
                replaceTasks(detail.project.id, withRequiredMilestones(body.tasks), teamIds)
            }

            call.respond(ProjectDetail.from(detail.project.id, currentUserID(), currentRole())!!)
        }
    }
}
