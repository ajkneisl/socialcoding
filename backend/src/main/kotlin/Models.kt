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

@Serializable
data class GoogleLoginRequest(val credential: String)

@Serializable data class LoginResponse(val token: String, val user: UserDto)

@Serializable
data class CreateProjectRequest(
    val title: String,
    val description: String,
    val longDescription: String? = null,
    val repoUrl: String? = null,
    val siteUrl: String? = null,
)

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
