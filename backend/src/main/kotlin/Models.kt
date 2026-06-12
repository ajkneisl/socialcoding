package com.socialcoding

import kotlinx.serialization.Serializable

enum class Role {
  MEMBER,
  BOARD,
}

enum class ProjectStatus {
  PENDING,
  APPROVED,
  REJECTED,
}

@Serializable
data class PersonDto(
    val id: Long,
    val name: String,
    val joinedTerm: String?,
    val gradYear: Int?,
    val github: String?,
    val linkedin: String?,
    val website: String?,
    val company: String?,
    val role: Role,
    val title: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val joinedTerm: String?,
    val gradYear: Int?,
    val github: String?,
    val linkedin: String?,
    val website: String?,
    val company: String?,
    val title: String? = null,
    val avatarUrl: String? = null,
)

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

@Serializable
data class GoogleLoginRequest(val credential: String)

@Serializable data class LoginResponse(val token: String, val user: UserDto)

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

@Serializable
data class UpdateProfileRequest(
    val joinedTerm: String? = null,
    val gradYear: Int? = null,
    val github: String? = null,
    val linkedin: String? = null,
    val website: String? = null,
    val company: String? = null,
)

@Serializable data class ReviewRequest(val note: String? = null)

@Serializable data class ApiError(val error: String)
