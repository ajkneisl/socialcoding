package com.socialcoding.projects.models

import kotlinx.serialization.Serializable

/**
 * An individual project.
 *
 * @param id The unique ID of the project.
 * @param title The title.
 * @param description The description.
 * @param repoUrl The optional GitHub repository URL.
 * @param siteUrl The optional site URL.
 * @param imageUrl The optional cover image.
 * @param status The board status of the project.
 * @param active If the project is active.
 * @param teamLeadName The name of the project's team lead (the owner if none is set).
 * @param teamLeadAvatarUrl The team lead's profile picture, if they have one.
 * @param submittedAt When the project was submitted in epoch ms.
 * @param reviewNote A note left by the board member who reviewed the project.
 * @param likes How many users have hearted the project.
 * @param liked Whether the requesting user has hearted the project.
 */
@Serializable
data class Project(
    val id: String,
    val title: String,
    val description: String,
    val repoUrl: String?,
    val siteUrl: String?,
    val imageUrl: String? = null,
    val status: ProjectStatus,
    val active: Boolean,
    val teamLeadName: String,
    val teamLeadAvatarUrl: String? = null,
    val submittedAt: Long,
    val reviewNote: String? = null,
    val likes: Long = 0,
    val liked: Boolean = false,
)
