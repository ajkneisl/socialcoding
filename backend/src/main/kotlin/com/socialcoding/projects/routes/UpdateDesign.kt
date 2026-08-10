package com.socialcoding.projects.routes

import com.socialcoding.api.db.query
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.people.Role
import com.socialcoding.projects.Projects
import com.socialcoding.projects.docs.encodeDesignDoc
import com.socialcoding.projects.docs.insertDesignDoc
import com.socialcoding.projects.docs.updateDesignDocContent
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.toUuid
import com.socialcoding.projects.toUuidOrNull
import com.socialcoding.user.currentRole
import com.socialcoding.user.currentUserID
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A request to update a project's details, and optionally the answers on its proposal.
 *
 * @param title The project title.
 * @param description The project description.
 * @param repoUrl The optional GitHub repository URL.
 * @param imageUrl The optional cover image.
 * @param designDoc The proposal answers. Omitted when only the details above are being edited —
 *   past semesters' answers are a record of what the team said at the time, so they stay put.
 */
@Serializable
private data class UpdateDesignRequest(
    val title: String,
    val description: String,
    val repoUrl: String? = null,
    val imageUrl: String? = null,
    val designDoc: DesignDocContent? = null,
)

/**
 * PUT /api/projects/{id}/design — update title, description, links, and the proposal answers.
 *
 * The title, description, repo link, and cover image are what the public showcase renders, so an
 * approved project that changes any of them drops back to [ProjectStatus.PENDING] for the board to
 * look at again. Proposal answers aren't public and don't trigger a re-review.
 */
val UPDATE_DESIGN: suspend RoutingContext.() -> Unit = handler@{
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val role = currentRole()
    val detail = projectDetail(projectID, currentUserID(), role) ?: throw NotFound("project")
    if (!detail.canEdit) throw InvalidAuthorization()

    val body = call.receive<UpdateDesignRequest>()
    if (body.title.isBlank() || body.description.isBlank()) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Title and description are required"),
        )
    }

    val proposal = detail.designDocs.firstOrNull { it.kind == DesignDocKind.INITIAL }
    if (body.designDoc != null) {
        // A proposal only stays editable for the semester it was filed in; after that the team
        // edits the semester doc instead, and this route is just the project's details.
        if (proposal != null && proposal.semester != detail.currentSemester) {
            return@handler call.respond(
                HttpStatusCode.BadRequest,
                APIError(
                    "The project proposal is from ${proposal.semester} and can no longer be edited"
                ),
            )
        }
        // Nothing to file a proposal alongside: this semester is already spoken for by a check-in.
        if (proposal == null &&
            detail.designDocs.any { it.semester == detail.currentSemester }) {
            return@handler call.respond(
                HttpStatusCode.BadRequest,
                APIError("This semester's design doc isn't a proposal"),
            )
        }
    }

    val newTitle = body.title.trim()
    val newDescription = body.description.trim()
    val newRepoUrl = body.repoUrl?.trim()?.ifBlank { null }
    val newImageUrl = body.imageUrl?.trim()?.ifBlank { null }

    // Board members are the ones reviewing, so their edits don't send the project back to
    // themselves.
    val needsReview =
        detail.project.status == ProjectStatus.APPROVED &&
            role != Role.BOARD &&
            (newTitle != detail.project.title ||
                newDescription != detail.project.description ||
                newRepoUrl != detail.project.repoUrl ||
                newImageUrl != detail.project.imageUrl)

    query {
        Projects.update({ Projects.id eq projectID }) {
            it[title] = newTitle
            it[description] = newDescription
            it[repoUrl] = newRepoUrl
            it[imageUrl] = newImageUrl
            if (needsReview) {
                it[status] = ProjectStatus.PENDING
                it[reviewedBy] = null
                it[reviewNote] = null
                it[submittedAt] = System.currentTimeMillis()
            }
        }

        if (body.designDoc != null) {
            val encoded = encodeDesignDoc(body.designDoc)
            // Projects created before design docs were rows have nothing to update, so file one.
            if (proposal == null) {
                insertDesignDoc(
                    projectID = projectID,
                    semester = detail.currentSemester,
                    kind = DesignDocKind.INITIAL,
                    content = encoded,
                )
            } else {
                updateDesignDocContent(proposal.id.toUuid(), encoded)
            }
        }
    }

    call.respond(projectDetail(projectID, currentUserID(), role)!!)
}
