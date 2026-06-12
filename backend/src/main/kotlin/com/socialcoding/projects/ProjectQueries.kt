package com.socialcoding.projects

import com.socialcoding.db.ProjectMembers
import com.socialcoding.db.ProjectTasks
import com.socialcoding.db.Projects
import com.socialcoding.db.Users
import com.socialcoding.people.Role
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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

fun projectsWithOwners() =
    Projects.join(Users, JoinType.INNER, Projects.ownerId, Users.id).selectAll()

/** Must be inside a transaction. */
fun memberIdsOf(projectId: Long): List<Long> =
    ProjectMembers.selectAll()
        .where { ProjectMembers.projectId eq projectId }
        .map { it[ProjectMembers.userId] }

/** Every design doc carries these two milestones; they can't be removed. */
fun withRequiredMilestones(tasks: List<TaskInput>): List<TaskInput> {
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
fun replaceTasks(projectId: Long, tasks: List<TaskInput>, teamIds: Set<Long>) {
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
fun loadDetail(projectId: Long, userId: Long, role: Role): ProjectDetailDto? = transaction {
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
