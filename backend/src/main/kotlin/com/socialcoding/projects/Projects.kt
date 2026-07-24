package com.socialcoding.projects

import com.socialcoding.db.Users
import com.socialcoding.projects.models.Project
import com.socialcoding.projects.models.ProjectStatus
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object Projects : Table("projects") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val title = varchar("title", 200)
    val description = text("description")
    val active = bool("active").default(true)
    val repoUrl = varchar("repo_url", 512).nullable()
    val siteUrl = varchar("site_url", 512).nullable()
    val imageUrl = varchar("image_url", 512).nullable()
    val ownerId = uuid("owner_id").references(Users.id)
    val teamLeadId = uuid("team_lead_id").references(Users.id).nullable()
    val designDoc = text("design_doc").nullable()
    val status =
        enumerationByName("status", 16, ProjectStatus::class).default(ProjectStatus.PENDING)
    val submittedAt = long("submitted_at")
    val reviewedBy = uuid("reviewed_by").references(Users.id).nullable()
    val reviewNote = varchar("review_note", 1000).nullable()
    val discordChannelId = varchar("discord_channel_id", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Convert a [ResultRow] into a [Project]. Expects the row to come from [projectsWithOwners], which
 * joins the owner and (optionally) the team lead; the lead's name and avatar fall back to the
 * owner's when no team lead is set.
 */
fun ResultRow.toProject(): Project {
    val leadJoined = getOrNull(ProjectLead[Users.id]) != null
    return Project(
        id = this[Projects.id].toString(),
        title = this[Projects.title],
        description = this[Projects.description],
        active = this[Projects.active],
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
