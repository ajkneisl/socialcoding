package com.socialcoding.projects

import com.socialcoding.db.Projects
import com.socialcoding.db.ProjectTasks
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow

enum class ProjectStatus {
  PENDING,
  APPROVED,
  REJECTED,
}

@Serializable
data class ProjectDto(
    val id: Long,
    val title: String,
    val description: String,
    val longDescription: String?,
    val repoUrl: String?,
    val siteUrl: String?,
    val status: ProjectStatus,
    val active: Boolean,
    val ownerName: String,
    val submittedAt: Long,
    val reviewNote: String? = null,
)

/** Answers to the design doc questions. Stored as JSON in Projects.designDoc. */
@Serializable
data class DesignDocContent(
    // About your Project
    val utilization: String = "",
    val services: String = "",
    val accessLocation: String = "",
    val intendedUsers: String = "",
    val goal: String = "",
    val usefulness: String = "",
    val demographic: String = "",
    val impact: String = "",
    val differentiation: String = "",
    val niceToHaves: String = "",
    // Architecture
    val serverNeeds: String = "",
    val databaseNeeds: String = "",
    val dataSchema: String = "",
    val dataProcurement: String = "",
    val dataProcessing: String = "",
    val softwareStack: String = "",
    // Teamwork
    val contributionExpectations: String = "",
    val communicationPlan: String = "",
)

@Serializable
data class TaskDto(
    val id: Long,
    val name: String,
    val assigneeIds: List<Long>,
    val dueDate: String,
    val dependsOn: List<Long>,
    val milestone: Boolean,
)

/** Task as submitted by the client; dependencies reference indices into the submitted list. */
@Serializable
data class TaskInput(
    val name: String,
    val assigneeIds: List<Long> = emptyList(),
    val dueDate: String = "",
    val dependsOn: List<Int> = emptyList(),
    val milestone: Boolean = false,
)

@Serializable
data class ProjectMemberDto(val id: Long, val name: String, val avatarUrl: String? = null)

@Serializable
data class ProjectDetailDto(
    val project: ProjectDto,
    val designDoc: DesignDocContent,
    val teamLeadId: Long,
    val members: List<ProjectMemberDto>,
    val tasks: List<TaskDto>,
    val canEdit: Boolean,
    val canManageTeam: Boolean,
)

/** Decodes the comma-separated id columns on ProjectTasks. */
fun String.toIdList(): List<Long> = split(',').mapNotNull { it.trim().toLongOrNull() }

fun ResultRow.toTask() =
    TaskDto(
        id = this[ProjectTasks.id],
        name = this[ProjectTasks.name],
        assigneeIds = this[ProjectTasks.assigneeIds].toIdList(),
        dueDate = this[ProjectTasks.dueDate],
        dependsOn = this[ProjectTasks.dependsOnIds].toIdList(),
        milestone = this[ProjectTasks.milestone],
    )

fun ResultRow.toProject(ownerName: String) =
    ProjectDto(
        id = this[Projects.id],
        title = this[Projects.title],
        description = this[Projects.description],
        longDescription = this[Projects.longDescription],
        active = this[Projects.active],
        repoUrl = this[Projects.repoUrl],
        siteUrl = this[Projects.siteUrl],
        status = this[Projects.status],
        ownerName = ownerName,
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )
