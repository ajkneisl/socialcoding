package com.socialcoding.projects

import com.socialcoding.board.BoardSettings
import com.socialcoding.board.PresentationDates
import com.socialcoding.people.Role
import com.socialcoding.people.Users
import com.socialcoding.projects.docs.designDocsOf
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.members.models.ProjectMember
import com.socialcoding.projects.models.PendingProject
import com.socialcoding.projects.models.Project
import com.socialcoding.projects.models.ProjectDetail
import com.socialcoding.projects.models.ProjectShowcase
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.tasks.toTask
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

/** Aliased [Users] for the team lead, left-joined so projects without a lead still come through. */
val ProjectLead = Users.alias("project_lead")

fun projectsWithOwners() =
    Projects.join(Users, JoinType.INNER, Projects.ownerId, Users.id)
        .join(
            ProjectLead,
            JoinType.LEFT,
            Projects.teamLeadId,
            ProjectLead[Users.id],
        )
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

/** The presentation milestones a design doc carries, in the order they're filed. */
val PRESENTATION_MILESTONES = listOf("MVP Presentation", "Final Presentation")

private fun TaskInput.isPresentationMilestone() =
    PRESENTATION_MILESTONES.any { it.equals(name.trim(), ignoreCase = true) }

/**
 * Drops every task matching [drop], remapping dependency indices around the removals so the
 * survivors still point at the same tasks.
 */
private fun List<TaskInput>.dropTasks(drop: (TaskInput) -> Boolean): List<TaskInput> {
    val kept = mutableListOf<TaskInput>()
    val remapped = arrayOfNulls<Int>(size)
    forEachIndexed { i, task ->
        if (drop(task)) return@forEachIndexed
        remapped[i] = kept.size
        kept += task
    }
    return kept.map { task ->
        task.copy(dependsOn = task.dependsOn.mapNotNull { remapped.getOrNull(it) })
    }
}

/**
 * Stamps the MVP and Final Presentation milestones onto [tasks] with the board's [dates].
 *
 * The milestones belong to the semester their design doc was filed in, so any already present —
 * last semester's, carrying last semester's dates — are dropped and replaced by a fresh pair.
 * Everything else the team wrote is kept, with dependencies remapped around the removals.
 *
 * This runs when a doc is filed, not when deliverables are saved: between filings the task list is
 * the team's.
 */
fun withPresentationMilestones(
    tasks: List<TaskInput>,
    dates: PresentationDates,
): List<TaskInput> =
    tasks.dropTasks { it.isPresentationMilestone() } +
        listOf(
            TaskInput(
                name = PRESENTATION_MILESTONES[0],
                dueDate = dates.mvpDate,
                milestone = true,
            ),
            TaskInput(
                name = PRESENTATION_MILESTONES[1],
                dueDate = dates.finalDate,
                milestone = true,
            ),
        )

/**
 * Replaces [projectId]'s presentation milestones with a pair stamped from the board's current
 * [dates], leaving the rest of its deliverables alone. Called when a team files a design doc for a
 * new semester. Must be inside a transaction.
 */
fun stampPresentationMilestones(projectId: Uuid, dates: PresentationDates) {
    replaceTasks(
        projectId,
        withPresentationMilestones(currentTaskInputs(projectId), dates),
        memberIdsOf(projectId).toSet(),
    )
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
fun replaceTasks(projectId: Uuid, submitted: List<TaskInput>, teamIds: Set<Uuid>) {
    // An unnamed row is a task the team started and abandoned in the editor, not a deliverable.
    val tasks = submitted.dropTasks { it.name.isBlank() }

    ProjectTasks.deleteWhere { ProjectTasks.projectID eq projectId }
    val newIds = tasks.map { task ->
        ProjectTasks.insert {
            it[ProjectTasks.projectID] = projectId
            it[name] = task.name.trim().take(300)
            it[assigneeIDs] =
                task.assigneeIds
                    .mapNotNull { id -> id.toUuidOrNull() }
                    .filter { id -> id in teamIds }
                    .toIdJson()
            it[dueDate] = task.dueDate.trim().take(10)
            it[milestone] = task.milestone
        } get ProjectTasks.id
    }
    tasks.forEachIndexed { i, task ->
        val deps = task.dependsOn.filter { it != i }.mapNotNull { newIds.getOrNull(it) }.distinct()
        if (deps.isNotEmpty()) {
            ProjectTasks.update({ ProjectTasks.id eq newIds[i] }) {
                it[dependsOnIDs] = Json.encodeToString(deps)
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
 * breaking ties). Active and inactive projects share the ordering; callers split them by
 * [Project.active]. Must be inside a transaction.
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
            .where {
                (Projects.id eq projectID) and (Projects.status eq ProjectStatus.APPROVED)
            }
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
        .map {
            ProjectMember(
                it[Users.id].toString(),
                it[Users.name],
                it[Users.avatarUrl],
            )
        }

/**
 * Loads the full [ProjectDetail] for [projectID] as seen by [userID] with [role], or null if the
 * project doesn't exist or the viewer isn't allowed to see it (not on the team, not board, and the
 * project isn't approved).
 */
fun projectDetail(projectID: Uuid, userID: Uuid, role: Role): ProjectDetail? = transaction {
    val row =
        projectsWithOwners().where { Projects.id eq projectID }.firstOrNull()
            ?: return@transaction null
    val memberIds = memberIdsOf(projectID)
    val leadId = row[Projects.teamLeadId] ?: row[Projects.ownerId]
    val onTeam = userID in memberIds || userID == leadId || userID == row[Projects.ownerId]
    val isBoard = role == Role.BOARD

    if (!onTeam && !isBoard && row[Projects.status] != ProjectStatus.APPROVED) {
        return@transaction null
    }

    val members = membersByIds(memberIds + leadId)
    val pendingMembers = membersByIds(pendingMemberIdsOf(projectID))

    val tasks =
        ProjectTasks.selectAll()
            .where { ProjectTasks.projectID eq projectID }
            .orderBy(ProjectTasks.dueDate)
            .map { it.toTask() }

    ProjectDetail(
        project = row.toProject(),
        designDocs = designDocsOf(projectID),
        currentSemester = BoardSettings.currentSemester(),
        teamLeadID = leadId.toString(),
        members = members,
        pendingMembers = pendingMembers,
        tasks = tasks,
        canEdit = onTeam || isBoard,
        canManageTeam = userID == leadId || isBoard,
    )
}

/** Retrieve all projects that are not yet [ProjectStatus.APPROVED]. */
fun getPendingProjects(): List<PendingProject> = transaction {
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
