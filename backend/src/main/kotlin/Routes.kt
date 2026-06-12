package com.socialcoding

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private fun RoutingContext.currentUserId(): Long =
    call.principal<JWTPrincipal>()!!.subject!!.toLong()

private fun RoutingContext.currentRole(): Role =
    Role.valueOf(call.principal<JWTPrincipal>()!!.payload.getClaim("role").asString())

private fun projectsWithOwners() =
    Projects.join(Users, JoinType.INNER, Projects.ownerId, Users.id).selectAll()

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun decodeDesignDoc(raw: String?): DesignDocContent =
    raw?.let { runCatching { json.decodeFromString<DesignDocContent>(it) }.getOrNull() }
        ?: DesignDocContent()

/** Must be inside a transaction. */
private fun memberIdsOf(projectId: Long): List<Long> =
    ProjectMembers.selectAll()
        .where { ProjectMembers.projectId eq projectId }
        .map { it[ProjectMembers.userId] }

/** Every design doc carries these two milestones; they can't be removed. */
private fun withRequiredMilestones(tasks: List<TaskInput>): List<TaskInput> {
    val required = listOf("MVP Presentation", "Final Presentation")
    val missing = required.filter { name ->
        tasks.none { it.name.trim().equals(name, ignoreCase = true) }
    }
    return tasks.filter { it.name.isNotBlank() } +
        missing.map { TaskInput(name = it, milestone = true) }
}

/**
 * Replaces a project's deliverables. Dependencies in [tasks] reference indices into the submitted
 * list and are translated to row ids once everything is inserted. Must be inside a transaction.
 */
private fun replaceTasks(projectId: Long, tasks: List<TaskInput>, teamIds: Set<Long>) {
    ProjectTasks.deleteWhere { ProjectTasks.projectId eq projectId }
    val newIds = tasks.map { task ->
        ProjectTasks.insert {
            it[ProjectTasks.projectId] = projectId
            it[name] = task.name.trim().take(300)
            it[assigneeIds] = task.assigneeIds.filter { id -> id in teamIds }.joinToString(",")
            it[dueDate] = task.dueDate.trim().take(10)
            it[milestone] = task.milestone
        } get ProjectTasks.id
    }
    tasks.forEachIndexed { i, task ->
        val deps = task.dependsOn.filter { it != i }.mapNotNull { newIds.getOrNull(it) }.distinct()
        if (deps.isNotEmpty()) {
            ProjectTasks.update({ ProjectTasks.id eq newIds[i] }) {
                it[dependsOnIds] = deps.joinToString(",")
            }
        }
    }
}

/**
 * Loads the full design doc for [projectId], or null if it doesn't exist or [userId] may not see
 * it. Pending/rejected docs are visible only to the team and the board.
 */
private fun loadDetail(projectId: Long, userId: Long, role: Role): ProjectDetailDto? = transaction {
    val row =
        projectsWithOwners().where { Projects.id eq projectId }.firstOrNull()
            ?: return@transaction null
    val memberIds = memberIdsOf(projectId)
    val leadId = row[Projects.teamLeadId] ?: row[Projects.ownerId]
    val onTeam = userId in memberIds || userId == leadId || userId == row[Projects.ownerId]
    val isBoard = role == Role.BOARD
    if (!onTeam && !isBoard && row[Projects.status] != ProjectStatus.APPROVED) {
        return@transaction null
    }
    // The lead always shows on the team, even for legacy projects with no membership rows.
    val members =
        Users.selectAll()
            .where { Users.id inList (memberIds + leadId).distinct() }
            .orderBy(Users.name)
            .map { ProjectMemberDto(it[Users.id], it[Users.name], it[Users.avatarUrl]) }
    val tasks =
        ProjectTasks.selectAll()
            .where { ProjectTasks.projectId eq projectId }
            .orderBy(ProjectTasks.dueDate)
            .map { it.toTask() }
    ProjectDetailDto(
        project = row.toProject(row[Users.name]),
        designDoc = decodeDesignDoc(row[Projects.designDoc]),
        teamLeadId = leadId,
        members = members,
        tasks = tasks,
        canEdit = onTeam || isBoard,
        canManageTeam = userId == leadId || isBoard,
    )
}

fun Route.apiRoutes(google: GoogleVerifier, sessions: SessionTokens, boardEmails: Set<String>) {
    route("/api") {
        get("/people") {
            val people = transaction { Users.selectAll().orderBy(Users.name).map { it.toPerson() } }
            call.respond(people)
        }

        get("/projects") {
            val projects = transaction {
                projectsWithOwners()
                    .where { Projects.status eq ProjectStatus.APPROVED }
                    .orderBy(Projects.submittedAt)
                    .map { it.toProject(it[Users.name]) }
            }
            call.respond(projects)
        }

        post("/auth/google") {
            val identity = google.verify(call.receive<GoogleLoginRequest>().credential)

            val user = transaction {
                val existing =
                    Users.selectAll()
                        .where {
                            (Users.googleSub eq identity.sub) or (Users.email eq identity.email)
                        }
                        .firstOrNull()
                // BOARD_EMAILS grants board access; seeded/claimed board members keep theirs.
                val role =
                    when {
                        identity.email.lowercase() in boardEmails -> Role.BOARD
                        existing != null -> existing[Users.role]
                        else -> Role.MEMBER
                    }
                if (existing == null) {
                    Users.insert {
                        it[googleSub] = identity.sub
                        it[email] = identity.email
                        it[name] = identity.name
                        it[avatarUrl] = identity.picture
                        it[Users.role] = role
                        it[createdAt] = System.currentTimeMillis()
                    }
                } else {
                    // Claims seeded rows and keeps profile/role in sync with Google + board list.
                    Users.update({ Users.id eq existing[Users.id] }) {
                        it[googleSub] = identity.sub
                        it[avatarUrl] = identity.picture
                        it[Users.role] = role
                    }
                }
                Users.selectAll().where { Users.email eq identity.email }.first().toUser()
            }

            call.respond(LoginResponse(sessions.issue(user.id, user.role), user))
        }

        authenticate("session") {
            get("/me") {
                val userId = currentUserId()
                val user = transaction {
                    Users.selectAll().where { Users.id eq userId }.firstOrNull()?.toUser()
                }
                if (user == null) call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
                else call.respond(user)
            }

            post("/me") {
                val userId = currentUserId()
                val body = call.receive<UpdateProfileRequest>()
                val user = transaction {
                    Users.update({ Users.id eq userId }) {
                        it[joinedTerm] = body.joinedTerm
                        it[gradYear] = body.gradYear
                        it[github] = body.github
                        it[linkedin] = body.linkedin
                        it[website] = body.website
                        it[company] = body.company
                    }
                    Users.selectAll().where { Users.id eq userId }.first().toUser()
                }
                call.respond(user)
            }

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
                            it[designDoc] =
                                json.encodeToString(DesignDocContent.serializer(), body.designDoc)
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
                        it[designDoc] =
                            json.encodeToString(DesignDocContent.serializer(), body.designDoc)
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

            route("/board") {
                get("/projects") {
                    if (currentRole() != Role.BOARD) {
                        call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
                        return@get
                    }
                    val pending = transaction {
                        projectsWithOwners()
                            .where { Projects.status eq ProjectStatus.PENDING }
                            .orderBy(Projects.submittedAt)
                            .map { it.toProject(it[Users.name]) }
                    }
                    call.respond(pending)
                }

                post("/projects/{id}/{decision}") {
                    if (currentRole() != Role.BOARD) {
                        call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
                        return@post
                    }
                    val projectId = call.parameters["id"]?.toLongOrNull()
                    val decision = call.parameters["decision"]
                    if (
                        projectId == null ||
                            decision !in setOf("approve", "reject", "activate", "deactivate")
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Unknown project or decision"),
                        )
                        return@post
                    }
                    val note = runCatching { call.receive<ReviewRequest>().note }.getOrNull()
                    val reviewerId = currentUserId()
                    val updated = transaction {
                        Projects.update({ Projects.id eq projectId }) {
                            when (decision) {
                                "approve" -> {
                                    it[status] = ProjectStatus.APPROVED
                                    it[reviewedBy] = reviewerId
                                    it[reviewNote] = note
                                }
                                "reject" -> {
                                    it[status] = ProjectStatus.REJECTED
                                    it[reviewedBy] = reviewerId
                                    it[reviewNote] = note
                                }
                                "activate" -> it[active] = true
                                "deactivate" -> it[active] = false
                            }
                        }
                    }
                    if (updated == 0)
                        call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                    else call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
