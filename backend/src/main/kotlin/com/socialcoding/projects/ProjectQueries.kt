package com.socialcoding.projects

import com.socialcoding.board.PresentationDates
import com.socialcoding.db.MemberStatus
import com.socialcoding.db.ProjectMember
import com.socialcoding.db.ProjectMembers
import com.socialcoding.db.Users
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

// encodeDefaults keeps blank design doc answers present in responses instead of omitted.
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun decodeDesignDoc(raw: String?): DesignDocContent =
    raw?.let { runCatching { json.decodeFromString<DesignDocContent>(it) }.getOrNull() }
        ?: DesignDocContent()

fun encodeDesignDoc(doc: DesignDocContent): String =
    json.encodeToString(DesignDocContent.serializer(), doc)

/** Aliased [Users] for the team lead, left-joined so projects without a lead still come through. */
val ProjectLead = Users.alias("project_lead")

fun projectsWithOwners() =
    Projects.join(Users, JoinType.INNER, Projects.ownerId, Users.id)
        .join(ProjectLead, JoinType.LEFT, Projects.teamLeadId, ProjectLead[Users.id])
        .selectAll()

/** The accepted team members of a project. Must be inside a transaction. */
fun memberIdsOf(projectId: Uuid): List<Uuid> =
    ProjectMembers.selectAll()
        .where {
            (ProjectMembers.projectID eq projectId) and
                (ProjectMembers.status eq MemberStatus.ACCEPTED)
        }
        .map { it[ProjectMembers.userID] }

/** Users invited to a project who haven't accepted yet. Must be inside a transaction. */
fun pendingMemberIdsOf(projectId: Uuid): List<Uuid> =
    ProjectMembers.selectAll()
        .where {
            (ProjectMembers.projectID eq projectId) and
                (ProjectMembers.status eq MemberStatus.PENDING)
        }
        .map { it[ProjectMembers.userID] }

/**
 * Every design doc carries the MVP and Final Presentation milestones; they can't be removed and
 * their due dates are inherited from the board's [dates] rather than set by the team. Any submitted
 * task matching a milestone name is normalized to the milestone (name, flag, board date); missing
 * milestones are appended.
 */
fun withRequiredMilestones(tasks: List<TaskInput>, dates: PresentationDates): List<TaskInput> {
    val required = linkedMapOf(
        "MVP Presentation" to dates.mvpDate,
        "Final Presentation" to dates.finalDate,
    )
    val normalized = tasks.filter { it.name.isNotBlank() }.map { task ->
        val match = required.keys.firstOrNull { it.equals(task.name.trim(), ignoreCase = true) }
        if (match != null) task.copy(name = match, milestone = true, dueDate = required.getValue(match))
        else task
    }
    val missing = required.filterKeys { name -> normalized.none { it.name.equals(name, ignoreCase = true) } }
    return normalized + missing.map { (name, date) -> TaskInput(name = name, dueDate = date, milestone = true) }
}

/**
 * Re-applies the required presentation milestones to every project, stamping them with the board's
 * current [dates] — used at the start of a new semester. Existing deliverables are preserved; the
 * MVP/Final milestones are added if missing and their due dates refreshed. Returns the number of
 * projects touched.
 */
fun syncMilestonesToAllProjects(dates: PresentationDates): Int = transaction {
    val projectIds = Projects.selectAll().map { it[Projects.id] }
    projectIds.forEach { projectId ->
        val inputs = currentTaskInputs(projectId)
        val teamIds = memberIdsOf(projectId).toSet()
        replaceTasks(projectId, withRequiredMilestones(inputs, dates), teamIds)
    }
    projectIds.size
}

/**
 * Loads a project's current tasks as [TaskInput]s, with dependencies expressed as indices into the
 * returned list (the inverse of [replaceTasks]). Must be inside a transaction.
 */
fun currentTaskInputs(projectId: Uuid): List<TaskInput> {
    val rows =
        ProjectTasks.selectAll()
            .where { ProjectTasks.projectID eq projectId }
            .orderBy(ProjectTasks.dueDate)
            .map { it.toTask() }
    val indexById = rows.mapIndexed { i, t -> t.id to i }.toMap()
    return rows.map { t ->
        TaskInput(
            name = t.name,
            assigneeIds = t.assigneeIds,
            dueDate = t.dueDate,
            dependsOn = t.dependsOn.mapNotNull { indexById[it] },
            milestone = t.milestone,
        )
    }
}

/**
 * Replaces a project's deliverables. Dependencies in [tasks] reference indices into the submitted
 * list and are translated to row ids once everything is inserted. Must be inside a transaction.
 */
fun replaceTasks(projectId: Uuid, tasks: List<TaskInput>, teamIds: Set<Uuid>) {
    ProjectTasks.deleteWhere { ProjectTasks.projectID eq projectId }
    val newIds = tasks.map { task ->
        ProjectTasks.insert {
            it[ProjectTasks.projectID] = projectId
            it[name] = task.name.trim().take(300)
            it[assigneeIDs] =
                task.assigneeIds.mapNotNull { id -> id.toUuidOrNull() }
                    .filter { id -> id in teamIds }
                    .joinToString(",")
            it[dueDate] = task.dueDate.trim().take(10)
            it[milestone] = task.milestone
        } get ProjectTasks.id
    }
    tasks.forEachIndexed { i, task ->
        val deps = task.dependsOn.filter { it != i }.mapNotNull { newIds.getOrNull(it) }.distinct()
        if (deps.isNotEmpty()) {
            ProjectTasks.update({ ProjectTasks.id eq newIds[i] }) {
                it[dependsOnIDs] = deps.joinToString(",")
            }
        }
    }
}

/**
 * Every project [userId] owns, leads, or has accepted membership on, regardless of board status.
 * Pending invites are excluded; those surface separately via [invitesForUser].
 */
fun projectsForUser(userId: Uuid): List<Project> = transaction {
    val memberOf =
        ProjectMembers.selectAll()
            .where {
                (ProjectMembers.userID eq userId) and
                    (ProjectMembers.status eq MemberStatus.ACCEPTED)
            }
            .map { it[ProjectMembers.projectID] }
    projectsWithOwners()
        .where {
            (Projects.ownerId eq userId) or
                (Projects.teamLeadId eq userId) or
                (Projects.id inList memberOf)
        }
        .orderBy(Projects.submittedAt)
        .map { it.toProject() }
}

/** Projects [userId] has a pending invite to. */
fun invitesForUser(userId: Uuid): List<Project> = transaction {
    val invitedTo =
        ProjectMembers.selectAll()
            .where {
                (ProjectMembers.userID eq userId) and
                    (ProjectMembers.status eq MemberStatus.PENDING)
            }
            .map { it[ProjectMembers.projectID] }
    if (invitedTo.isEmpty()) return@transaction emptyList()
    projectsWithOwners()
        .where { Projects.id inList invitedTo }
        .orderBy(Projects.submittedAt)
        .map { it.toProject() }
}

/**
 * Approved projects, shown publicly on the home and projects pages, ordered by hearts. When
 * [userID] is given (the viewer is signed in), each project carries that user's like state.
 */
fun listApprovedProjects(userID: Uuid? = null): List<Project> = transaction {
    val projects =
        projectsWithOwners()
            .where { Projects.status eq ProjectStatus.APPROVED }
            .map { it.toProject() }
    withLikes(projects, userID)
}

/**
 * Attaches like counts and the viewer's like state, then orders by hearts (most first, newest
 * breaking ties). Active and inactive projects share the ordering; callers split them by [Project.active].
 * Must be inside a transaction.
 */
private fun withLikes(projects: List<Project>, userID: Uuid?): List<Project> {
    if (projects.isEmpty()) return projects
    val ids = projects.map { Uuid.parse(it.id) }
    val counts = likeCountsFor(ids)
    val mine = userID?.let { likedProjectIds(it, ids) } ?: emptySet()
    return projects
        .map { project ->
            val id = Uuid.parse(project.id)
            project.copy(likes = counts[id] ?: 0, liked = id in mine)
        }
        .sortedWith(compareByDescending<Project> { it.likes }.thenByDescending { it.submittedAt })
}

/**
 * The public showcase for an approved project: the project (with likes), its team lead, and the
 * full team. Returns null if the project isn't approved (so it isn't publicly listed). [userID],
 * when present, fills in the viewer's like state.
 */
fun projectShowcase(projectID: Uuid, userID: Uuid?): ProjectShowcase? = transaction {
    val row =
        projectsWithOwners()
            .where { (Projects.id eq projectID) and (Projects.status eq ProjectStatus.APPROVED) }
            .firstOrNull() ?: return@transaction null

    val leadId = row[Projects.teamLeadId] ?: row[Projects.ownerId]
    val likes = likeCountsFor(listOf(projectID))[projectID] ?: 0
    val liked = userID != null && projectID in likedProjectIds(userID, listOf(projectID))

    ProjectShowcase(
        project = row.toProject().copy(likes = likes, liked = liked),
        teamLeadID = leadId.toString(),
        members = membersByIds(memberIdsOf(projectID) + leadId),
    )
}

/** Loads [ProjectMember]s for the given user ids, ordered by name. Must be inside a transaction. */
fun membersByIds(ids: Collection<Uuid>): List<ProjectMember> =
    Users.selectAll()
        .where { Users.id inList ids.distinct() }
        .orderBy(Users.name)
        .map { ProjectMember(it[Users.id].toString(), it[Users.name], it[Users.avatarUrl]) }

/**
 * Every project that isn't approved yet — those awaiting board review and those the board rejected
 * (which the team may still resubmit) — paired with its team, for the board panel. Pending projects
 * come first so the actionable review queue stays on top.
 */
fun pendingProjects(): List<PendingProject> = transaction {
    projectsWithOwners()
        .where { Projects.status neq ProjectStatus.APPROVED }
        .orderBy(Projects.submittedAt)
        .sortedBy { it[Projects.status] != ProjectStatus.PENDING }
        .map { row ->
            val project = row.toProject()
            val leadID = row[Projects.teamLeadId] ?: row[Projects.ownerId]
            PendingProject(
                project,
                leadID.toString(),
                membersByIds(memberIdsOf(row[Projects.id]) + leadID),
            )
        }
}
