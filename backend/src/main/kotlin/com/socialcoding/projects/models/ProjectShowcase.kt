package com.socialcoding.projects.models

import com.socialcoding.projects.members.models.ProjectMember
import kotlinx.serialization.Serializable

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
