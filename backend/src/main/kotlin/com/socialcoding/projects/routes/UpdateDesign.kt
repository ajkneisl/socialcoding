package com.socialcoding.projects.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.auth.currentUserID
import com.socialcoding.common.APIError
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.NotFound
import com.socialcoding.projects.Projects
import com.socialcoding.projects.encodeDesignDoc
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A request to update a project's design doc.
 *
 * @param title The project title.
 * @param description The project description.
 * @param repoUrl The optional GitHub repository URL.
 * @param imageUrl The optional cover image.
 * @param designDoc The design doc answers.
 */
@Serializable
private data class UpdateDesignRequest(
    val title: String,
    val description: String,
    val repoUrl: String? = null,
    val imageUrl: String? = null,
    val designDoc: DesignDocContent,
)

/** PUT /api/projects/{id}/design — update title, description, repo link, and design doc. */
val UPDATE_DESIGN: suspend RoutingContext.() -> Unit = handler@{
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    if (!detail.canEdit) throw InvalidAuthorization()

    val body = call.receive<UpdateDesignRequest>()
    if (body.title.isBlank() || body.description.isBlank()) {
        return@handler call.respond(
            HttpStatusCode.BadRequest,
            APIError("Title and description are required"),
        )
    }

    transaction {
        Projects.update({ Projects.id eq projectID }) {
            it[title] = body.title.trim()
            it[description] = body.description.trim()
            it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
            it[imageUrl] = body.imageUrl?.trim()?.ifBlank { null }
            it[designDoc] = encodeDesignDoc(body.designDoc)
        }
    }

    call.respond(projectDetail(projectID, currentUserID(), currentRole())!!)
}
