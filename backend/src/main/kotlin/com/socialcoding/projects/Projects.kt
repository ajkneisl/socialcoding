package com.socialcoding.projects

import com.socialcoding.api.db.SqlTable
import com.socialcoding.people.Users
import com.socialcoding.projects.models.Project
import com.socialcoding.projects.models.ProjectStatus
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

/** A project. */
@SqlTable
object Projects : Table("projects") {
    /** A unique UUID for the project. */
    val id = uuid("id").clientDefault { Uuid.random() }

    /** The title of the project. */
    val title = varchar("title", 200)

    /** The description of the project. */
    val description = text("description")

    /** The project is marked as active / inactive by the board members. */
    val active = bool("active").default(true)

    /** The GitHub repo for the project. */
    val repoUrl = varchar("repo_url", 512).nullable()

    /** If there's a URL for the project. */
    val siteUrl = varchar("site_url", 512).nullable()

    /** A picture for the project. */
    val imageUrl = varchar("image_url", 512).nullable()
    val ownerId = uuid("owner_id").references(Users.id)
    val teamLeadId = uuid("team_lead_id").references(Users.id).nullable()
    val designDoc = text("design_doc").nullable()
    val lookingForTeammates = bool("looking_for_teammates").default(false)
    val status =
        enumerationByName("status", 16, ProjectStatus::class).default(ProjectStatus.PENDING)

    /** When the project was proposed. */
    val submittedAt = long("submitted_at")

    /** Which board member reviewed the project. */
    val reviewedBy = uuid("reviewed_by").references(Users.id).nullable()

    /** If there's any note on the project. */
    val reviewNote = varchar("review_note", 1000).nullable()

    /** The Discord channel to send updates for the project. */
    val discordChannelId = varchar("discord_channel_id", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}

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
 * @param lookingForTeammates Whether the team is looking for more people to join.
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
    val lookingForTeammates: Boolean = false,
    val teamLeadName: String,
    val teamLeadAvatarUrl: String? = null,
    val submittedAt: Long,
    val reviewNote: String? = null,
    val likes: Long = 0,
    val liked: Boolean = false,
)

/**
 * Convert a [ResultRow] into a [Project]. Expects the row to come from [projectsWithOwners], which
 * joins the owner and (optionally) the team lead; the lead's name and avatar fall back to the
 * owner's when no team lead is set.
 */
fun ResultRow.toProject(): Project {
    return Project(
        id = this[Projects.id].toString(),
        title = this[Projects.title],
        description = this[Projects.description],
        active = this[Projects.active],
        lookingForTeammates = this[Projects.lookingForTeammates],
        teamLeadName = if (leadJoined) this[ProjectLead[Users.name]] else this[Users.name],
        teamLeadAvatarUrl =
            if (leadJoined) this[ProjectLead[Users.avatarUrl]] else this[Users.avatarUrl],
        repoUrl = this[Projects.repoUrl],
        siteUrl = this[Projects.siteUrl],
        imageUrl = this[Projects.imageUrl],
        status = this[Projects.status],
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )
}
