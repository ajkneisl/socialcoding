package com.socialcoding.projects.models

import com.socialcoding.projects.tasks.ProjectTask
import com.socialcoding.projects.members.models.ProjectMember
import kotlinx.serialization.Serializable

/**
 * All details pertaining to a [Project]. Built by `projectDetail` in the queries layer.
 *
 * @param project The project itself.
 * @param designDocs Every design doc the project has filed, newest semester first.
 * @param currentSemester The semester docs are being filed for, so clients can tell which of
 *   [designDocs] is the live one and whether this semester's is still missing.
 * @param teamLeadID The ID of the team lead user.
 * @param members All accepted members of the project.
 * @param pendingMembers Users invited to the project who haven't accepted yet.
 * @param tasks All current tasks for the project.
 * @param canEdit If the requesting user may edit the project.
 * @param canManageTeam If the requesting user may manage the team.
 */
@Serializable
data class ProjectDetail(
    val project: Project,
    val designDocs: List<DesignDocEntry>,
    val currentSemester: String,
    val teamLeadID: String,
    val members: List<ProjectMember>,
    val pendingMembers: List<ProjectMember>,
    val tasks: List<ProjectTask>,
    val canEdit: Boolean,
    val canManageTeam: Boolean,
)
