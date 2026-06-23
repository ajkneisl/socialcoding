package com.socialcoding.projects

import com.socialcoding.db.ProjectMember
import com.socialcoding.db.Role
import com.socialcoding.db.Users
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Projects : Table("projects") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val title = varchar("title", 200)
    val description = text("description")
    val active = bool("active").default(true)
    val repoUrl = varchar("repo_url", 512).nullable()
    val siteUrl = varchar("site_url", 512).nullable()
    val ownerId = uuid("owner_id").references(Users.id)
    val teamLeadId = uuid("team_lead_id").references(Users.id).nullable()
    val designDoc = text("design_doc").nullable()
    val status =
        enumerationByName("status", 16, ProjectStatus::class).default(ProjectStatus.PENDING)
    val submittedAt = long("submitted_at")
    val reviewedBy = uuid("reviewed_by").references(Users.id).nullable()
    val reviewNote = varchar("review_note", 1000).nullable()

    override val primaryKey = PrimaryKey(id)
}

enum class ProjectStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

/**
 * An individual project.
 *
 * @param id The unique ID of the project.
 * @param title The title.
 * @param description The description.
 * @param repoUrl The optional GitHub repository URL.
 * @param siteUrl The optional site URL.
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
    val status: ProjectStatus,
    val active: Boolean,
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
        status = this[Projects.status],
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )
}

/**
 * All details pertaining to a [Project].
 *
 * @param project The project itself.
 * @param designDoc The design doc for the project.
 * @param teamLeadID The ID of the team lead user.
 * @param members All members of the project.
 * @param tasks All current tasks for the project.
 * @param canEdit If the requesting user may edit the project.
 * @param canManageTeam If the requesting user may manage the team.
 */
@Serializable
data class ProjectDetail(
    val project: Project,
    val designDoc: DesignDocContent,
    val teamLeadID: String,
    val members: List<ProjectMember>,
    val tasks: List<ProjectTask>,
    val canEdit: Boolean,
    val canManageTeam: Boolean,
) {
    companion object {
        /**
         * Load the project detail.
         *
         * @param projectID The project to load.
         * @param userID The ID of the user requesting.
         * @param role The role of [userID].
         */
        fun from(projectID: Uuid, userID: Uuid, role: Role): ProjectDetail? = transaction {
            val row =
                projectsWithOwners().where { Projects.id eq projectID }.firstOrNull()
                    ?: return@transaction null
            val memberIds = memberIdsOf(projectID)
            val leadId = row[Projects.teamLeadId] ?: row[Projects.ownerId]
            val onTeam = userID in memberIds || userID == leadId || userID == row[Projects.ownerId]
            val isBoard = role == Role.BOARD

            if (!onTeam && !isBoard && row[Projects.status] != ProjectStatus.APPROVED) {
                return@transaction null
            }

            val members = membersByIds(memberIds + leadId)

            val tasks =
                ProjectTasks.selectAll()
                    .where { ProjectTasks.projectID eq projectID }
                    .orderBy(ProjectTasks.dueDate)
                    .map { it.toTask() }

            ProjectDetail(
                project = row.toProject(),
                designDoc = decodeDesignDoc(row[Projects.designDoc]),
                teamLeadID = leadId.toString(),
                members = members,
                tasks = tasks,
                canEdit = onTeam || isBoard,
                canManageTeam = userID == leadId || isBoard,
            )
        }
    }
}

/**
 * A pending [Project] paired with its team, for the board review queue.
 *
 * @param project The project awaiting review.
 * @param teamLeadID The ID of the team lead.
 * @param members Everyone on the team, including the lead.
 */
@Serializable
data class PendingProject(
    val project: Project,
    val teamLeadID: String,
    val members: List<ProjectMember>,
)

/**
 * The public showcase view of an approved [Project]: who built it and how many hearts it has, but
 * none of the internal design doc.
 *
 * @param project The project, with its like count and the viewer's like state.
 * @param teamLeadID The ID of the team lead.
 * @param members Everyone on the team, including the lead.
 */
@Serializable
data class ProjectShowcase(
    val project: Project,
    val teamLeadID: String,
    val members: List<ProjectMember>,
)

/** A [Project]'s answers to the Design Document. */
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
