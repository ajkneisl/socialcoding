package com.socialcoding.projects.routes

import com.socialcoding.auth.optionalUserID
import com.socialcoding.projects.listApprovedProjects
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects — every approved project, ordered by hearts. */
val LIST_PROJECTS: suspend RoutingContext.() -> Unit = {
    call.respond(listApprovedProjects(optionalUserID()))
}
