package com.socialcoding.projects.models

import com.socialcoding.projects.members.models.ProjectMember
import kotlinx.serialization.Serializable

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
