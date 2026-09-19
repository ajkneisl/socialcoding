package com.socialcoding.projects.routes

import com.socialcoding.user.currentRole
import com.socialcoding.user.currentUserID
import com.socialcoding.common.NotFound
import com.socialcoding.projects.projectDetail
import com.socialcoding.projects.toUuidOrNull
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects/{id} — a single project's full design doc. */
val GET_PROJECT: suspend RoutingContext.() -> Unit = {
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val detail =
        projectDetail(projectID, currentUserID(), currentRole()) ?: throw NotFound("project")
    call.respond(detail)
}
