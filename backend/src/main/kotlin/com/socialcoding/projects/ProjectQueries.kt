package com.socialcoding.projects

import com.socialcoding.db.ProjectMember
import com.socialcoding.db.ProjectMembers
import com.socialcoding.db.Users
import kotlin.uuid.Uuid
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
fun memberIdsOf(projectId: Uuid): List<Uuid> =
    ProjectMembers.selectAll()
        .where { ProjectMembers.projectID eq projectId }
        .map { it[ProjectMembers.userID] }

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

/** Every project [userId] owns, leads, or is a member of, regardless of status. */
fun projectsForUser(userId: Uuid): List<Project> = transaction {
    val memberOf =
        ProjectMembers.selectAll()
            .where { ProjectMembers.userID eq userId }
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

/** Approved projects, shown publicly on the home and projects pages. */
fun listApprovedProjects(): List<Project> = transaction {
    projectsWithOwners()
        .where { Projects.status eq ProjectStatus.APPROVED }
        .orderBy(Projects.submittedAt)
        .map { it.toProject() }
}

/** Loads [ProjectMember]s for the given user ids, ordered by name. Must be inside a transaction. */
fun membersByIds(ids: Collection<Uuid>): List<ProjectMember> =
    Users.selectAll()
        .where { Users.id inList ids.distinct() }
        .orderBy(Users.name)
        .map { ProjectMember(it[Users.id].toString(), it[Users.name], it[Users.avatarUrl]) }

/** Every pending project paired with its team, for the board review queue. */
fun pendingProjects(): List<PendingProject> = transaction {
    projectsWithOwners()
        .where { Projects.status eq ProjectStatus.PENDING }
        .orderBy(Projects.submittedAt)
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
