package com.socialcoding.projects.routes

import com.socialcoding.user.optionalUserID
import com.socialcoding.common.NotFound
import com.socialcoding.projects.projectShowcase
import com.socialcoding.projects.toUuidOrNull
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects/{id}/showcase — the public project page (no design doc). */
val SHOWCASE: suspend RoutingContext.() -> Unit = {
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    val showcase = projectShowcase(projectID, optionalUserID()) ?: throw NotFound("project")
    call.respond(showcase)
}
