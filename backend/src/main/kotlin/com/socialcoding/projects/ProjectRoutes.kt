package com.socialcoding.projects

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.auth.optionalUserID
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.ApiError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.db.MemberStatus
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Public project listing plus the authenticated design doc lifecycle. */
fun Route.projectRoutes() {
    // GET /api/projects
    // list every approved project, ordered by hearts; like state is filled in when signed in
    authenticate("session", optional = true) {
        get("/projects") { call.respond(listApprovedProjects(optionalUserID())) }

        // GET /api/projects/{id}/showcase
        // public project page: the project, its team, and hearts (no design doc)
        get("/projects/{id}/showcase") {
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val showcase =
                projectShowcase(projectID, optionalUserID()) ?: throw NotFound("project")
            call.respond(showcase)
        }
    }

    authenticate("session") {
        /**
         * A request to create a new project.
         *
         * @param title The project title.
         * @param description The project description.
         * @param repoUrl The optional GitHub repository URL.
         * @param imageUrl The optional cover image.
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
            val imageUrl: String? = null,
            val teamLeadId: String? = null,
            val memberIds: List<String> = emptyList(),
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
                    (body.memberIds.mapNotNull { it.toUuidOrNull() } +
                            userID +
                            listOfNotNull(body.teamLeadId?.toUuidOrNull()))
                        .distinct()
                val teamIds =
                    Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
                val leadID = body.teamLeadId?.toUuidOrNull()?.takeIf { it in teamIds } ?: userID
                val id =
                    Projects.insert {
                        it[title] = body.title.trim()
                        it[description] = body.description.trim()
                        it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                        it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
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
                        // The creator is on the team immediately; everyone else is invited.
                        it[status] =
                            if (memberID == userID) MemberStatus.ACCEPTED else MemberStatus.PENDING
                    }
                }
                replaceTasks(
                    id,
                    withRequiredMilestones(body.tasks, BoardSettings.presentationDates()),
                    teamIds.toSet(),
                )
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

        // GET /api/projects/presentation-dates
        // the board-set MVP/Final presentation dates that milestones inherit
        get("/projects/presentation-dates") { call.respond(BoardSettings.presentationDates()) }

        // GET /api/projects/invites
        // list the projects the signed-in user has a pending invite to
        get("/projects/invites") { call.respond(invitesForUser(currentUserID())) }

        // POST /api/projects/{id}/invite/accept
        // accept a pending invite, joining the project's team
        post("/projects/{id}/invite/accept") {
            val userID = currentUserID()
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val updated = transaction {
                ProjectMembers.update({
                    (ProjectMembers.projectID eq projectID) and
                        (ProjectMembers.userID eq userID) and
                        (ProjectMembers.status eq MemberStatus.PENDING)
                }) {
                    it[status] = MemberStatus.ACCEPTED
                }
            }
            if (updated == 0) throw NotFound("invite")
            call.respond(HttpStatusCode.OK)
        }

        // POST /api/projects/{id}/invite/decline
        // decline a pending invite, dropping the user from the project
        post("/projects/{id}/invite/decline") {
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

        // GET /api/projects/{id}
        // load a single project's full design doc
        get("/projects/{id}") {
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val detail =
                ProjectDetail.from(projectID, currentUserID(), currentRole())
                    ?: throw NotFound("project")

            call.respond(detail)
        }

        // POST /api/projects/{id}/resubmit
        // the team lead sends a rejected design doc back to the board for another review
        post("/projects/{id}/resubmit") {
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val detail =
                ProjectDetail.from(projectID, currentUserID(), currentRole())
                    ?: throw NotFound("project")
            if (!detail.canManageTeam) throw InvalidAuthorization()
            if (detail.project.status != ProjectStatus.REJECTED) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Only rejected projects can be resubmitted"),
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

            call.respond(ProjectDetail.from(projectID, currentUserID(), currentRole())!!)
        }

        // POST /api/projects/{id}/like
        // toggle the signed-in user's heart on an approved project
        post("/projects/{id}/like") {
            val userID = currentUserID()
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            if (!approvedProjectExists(projectID)) throw NotFound("project")

            call.respond(toggleLike(projectID, userID))
        }

        /**
         * A request to update a project's design doc.
         *
         * @param title The project title.
         * @param description The project description.
         * @param repoUrl The optional GitHub repository URL.
         * @param imageUrl The optional cover image.
         * @param designDoc The design doc answers.
         */
        @Serializable
        data class UpdateDesignRequest(
            val title: String,
            val description: String,
            val repoUrl: String? = null,
            val imageUrl: String? = null,
            val designDoc: DesignDocContent,
        )

        // PUT /api/projects/{id}/design
        // update a project's title, description, repo link, and design doc answers
        put("/projects/{id}/design") {
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val detail =
                ProjectDetail.from(projectID, currentUserID(), currentRole())
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
                Projects.update({ Projects.id eq projectID }) {
                    it[title] = body.title.trim()
                    it[description] = body.description.trim()
                    it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                    it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
                    it[designDoc] = encodeDesignDoc(body.designDoc)
                }
            }

            call.respond(ProjectDetail.from(projectID, currentUserID(), currentRole())!!)
        }

        /**
         * A request to replace a project's team.
         *
         * @param memberIds The IDs of the new team members.
         * @param teamLeadId The ID of the new team lead.
         */
        @Serializable
        data class UpdateMembersRequest(val memberIds: List<String>, val teamLeadId: String)

        // PUT /api/projects/{id}/members
        // replace a project's membership and team lead
        put("/projects/{id}/members") {
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val detail =
                ProjectDetail.from(projectID, currentUserID(), currentRole())
                    ?: throw NotFound("project")
            if (!detail.canManageTeam) throw InvalidAuthorization()

            val body = call.receive<UpdateMembersRequest>()

            val applied = transaction {
                val leadID = body.teamLeadId.toUuidOrNull() ?: return@transaction false
                val requestedIds =
                    (body.memberIds.mapNotNull { it.toUuidOrNull() } + leadID).distinct()
                val teamIds =
                    Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
                if (leadID !in teamIds) return@transaction false
                val ownerID =
                    Projects.selectAll()
                        .where { Projects.id eq projectID }
                        .first()[Projects.ownerId]
                val existing =
                    ProjectMembers.selectAll()
                        .where { ProjectMembers.projectID eq projectID }
                        .associate { it[ProjectMembers.userID] to it[ProjectMembers.status] }
                Projects.update({ Projects.id eq projectID }) {
                    it[teamLeadId] = leadID
                }
                // Drop anyone no longer on the team, keeping the rest at their current invite state.
                ProjectMembers.deleteWhere {
                    (ProjectMembers.projectID eq projectID) and
                        (ProjectMembers.userID notInList teamIds)
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
            val updated = ProjectDetail.from(projectID, currentUserID(), currentRole())
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
            val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
            val detail =
                ProjectDetail.from(projectID, currentUserID(), currentRole())
                    ?: throw NotFound("project")
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

            call.respond(ProjectDetail.from(projectID, currentUserID(), currentRole())!!)
        }
    }
}
