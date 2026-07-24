package com.socialcoding.people.models

import com.socialcoding.db.Role
import kotlinx.serialization.Serializable

/** Public profile shown on the People page; deliberately omits the email. */
@Serializable
data class Person(
    val id: String,
    val name: String,
    val gradYear: Int?,
    val github: String?,
    val linkedin: String?,
    val website: String?,
    val company: String?,
    val role: Role,
    val title: String? = null,
    val avatarUrl: String? = null,
)
