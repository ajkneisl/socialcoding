package com.socialcoding.projects.routes

import com.socialcoding.auth.currentUserID
import com.socialcoding.projects.projectsForUser
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects/mine — projects the signed-in user owns, leads, or is a member of. */
val MY_PROJECTS: suspend RoutingContext.() -> Unit = {
    call.respond(projectsForUser(currentUserID()))
}
