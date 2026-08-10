package com.socialcoding.projects.routes

import com.socialcoding.api.db.query
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.Projects
import com.socialcoding.projects.docs.encodeReturningDoc
import com.socialcoding.projects.docs.insertDesignDoc
import com.socialcoding.projects.docs.updateDesignDocContent
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.models.ReturningDocContent
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.stampPresentationMilestones
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
 * A request to file or edit this semester's returning design doc.
 *
 * @param designDoc The check-in answers.
 */
@Serializable private data class SemesterDocRequest(val designDoc: ReturningDocContent)

/**
 * PUT /api/projects/{id}/semester-doc — file or update this semester's returning design doc.
 *
 * Filing it is how a project comes back for another semester, so the first save also sends the
 * project to the board: it drops to PENDING with the previous review cleared, the same state a new
 * proposal starts in, and last semester's presentation milestones are swapped for this semester's.
 * Saving again only replaces the answers, so a team can keep editing while the board reviews.
 */
val UPDATE_SEMESTER_DOC: suspend RoutingContext.() -> Unit = handler@{
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    if (!detail.canEdit) throw InvalidAuthorization()

    val body = call.receive<SemesterDocRequest>()
    val existing = detail.designDocs.firstOrNull { it.semester == detail.currentSemester }
    // A project started this semester already has a doc for it — its proposal, which is edited
    // through /design. It doesn't also file a check-in on its first semester.
    if (existing?.kind == DesignDocKind.INITIAL) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("This semester's design doc is the project proposal"),
        )
    }

    val content = encodeReturningDoc(body.designDoc)
    query {
        if (existing == null) {
            insertDesignDoc(
                projectID = projectID,
                semester = detail.currentSemester,
                kind = DesignDocKind.RETURNING,
                content = content,
            )
            Projects.update({ Projects.id eq projectID }) {
                it[status] = ProjectStatus.PENDING
                it[reviewNote] = null
                it[reviewedBy] = null
                it[submittedAt] = System.currentTimeMillis()
            }
            // The old MVP/Final dates were last semester's; the team presents on the new ones.
            stampPresentationMilestones(projectID, BoardSettings.presentationDates())
        } else {
            updateDesignDocContent(existing.id.toUuid(), content)
        }
    }

    call.respond(projectDetail(projectID, currentUserID(), currentRole())!!)
}
