package com.socialcoding.projects.routes

import com.socialcoding.user.currentRole
import com.socialcoding.user.currentUserID
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.APIError
import com.socialcoding.people.Users
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.Projects
import com.socialcoding.projects.TaskInput
import com.socialcoding.projects.docs.encodeDesignDoc
import com.socialcoding.projects.docs.insertDesignDoc
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.replaceTasks
import com.socialcoding.projects.toUuidOrNull
import com.socialcoding.projects.withPresentationMilestones
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * A request to create a new project.
 *
 * @param title The project title.
 * @param description The project description.
 * @param repoUrl The optional GitHub repository URL.
 * @param imageUrl The optional cover image.
 * @param teamLeadId The ID of the team lead; defaults to the creator.
 * @param memberIds The IDs of the team members.
 * @param designDoc The design doc answers.
 * @param tasks The initial deliverables.
 */
@Serializable
private data class CreateProjectRequest(
    val title: String,
    val description: String,
    val repoUrl: String? = null,
    val imageUrl: String? = null,
    val teamLeadId: String? = null,
    val memberIds: List<String> = emptyList(),
    val designDoc: DesignDocContent = DesignDocContent(),
    val tasks: List<TaskInput> = emptyList(),
)

/** POST /api/projects — submit a new project for board review. */
val CREATE_PROJECT: suspend RoutingContext.() -> Unit = handler@{
    val userID = currentUserID()
    val body = call.receive<CreateProjectRequest>()

    if (body.title.isBlank() || body.description.isBlank()) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Title and description are required"),
        )
    }

    // Both milestones are stamped from these, so submitting before they're set would file a
    // timeline with two dateless presentations on it.
    val dates = BoardSettings.presentationDates()
    if (dates.mvpDate.isBlank() || dates.finalDate.isBlank()) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("The board hasn't set this semester's presentation dates yet."),
        )
    }

    val projectID = transaction {
        val requestedIds =
            (body.memberIds.mapNotNull { it.toUuidOrNull() } +
                    userID +
                    listOfNotNull(body.teamLeadId?.toUuidOrNull()))
                .distinct()
        val teamIds =
            Users.selectAll().where { Users.id inList requestedIds }.map { it[Users.id] }
        val leadID = body.teamLeadId?.toUuidOrNull()?.takeIf { it in teamIds } ?: userID
        val submitted = System.currentTimeMillis()
        val id =
            Projects.insert {
                it[title] = body.title.trim()
                it[description] = body.description.trim()
                it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
                it[ownerId] = userID
                it[teamLeadId] = leadID
                it[status] = ProjectStatus.PENDING
                it[submittedAt] = submitted
            } get Projects.id
        // The proposal is this semester's design doc; every later semester adds a returning one.
        insertDesignDoc(
            projectID = id,
            semester = BoardSettings.currentSemester(),
            kind = DesignDocKind.INITIAL,
            content = encodeDesignDoc(body.designDoc),
            submittedAt = submitted,
        )
        teamIds.forEach { memberID ->
            ProjectMembers.insert {
                it[ProjectMembers.projectID] = id
                it[ProjectMembers.userID] = memberID
                // The creator is on the team immediately; everyone else is invited.
                it[status] =
                    if (memberID == userID) MemberStatus.ACCEPTED else MemberStatus.PENDING
            }
        }
        // The proposal is a design doc filing, so it stamps this semester's milestones.
        replaceTasks(
            id,
            withPresentationMilestones(body.tasks, dates),
            teamIds.toSet(),
        )
        id
    }

    call.respond(HttpStatusCode.Created, projectDetail(projectID, userID, currentRole())!!)
}
