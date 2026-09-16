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

    /** Who's the lead of the project. */
    val teamLeadId = uuid("team_lead_id").references(Users.id)

    /** The status of the project. */
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
 * Convert a [ResultRow] into a [Project]. Expects the row to come from [projectsWithLeads], which
 * joins the team lead.
 */
fun ResultRow.toProject(): Project {
    return Project(
        id = this[Projects.id].toString(),
        title = this[Projects.title],
        description = this[Projects.description],
        active = this[Projects.active],
        teamLeadName = this[Users.name],
        teamLeadAvatarUrl = this[Users.avatarUrl],
        repoUrl = this[Projects.repoUrl],
        siteUrl = this[Projects.siteUrl],
        imageUrl = this[Projects.imageUrl],
        status = this[Projects.status],
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )
}
