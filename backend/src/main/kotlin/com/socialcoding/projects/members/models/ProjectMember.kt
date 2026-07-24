package com.socialcoding.projects.members.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMember(val id: String, val name: String, val avatarUrl: String? = null)