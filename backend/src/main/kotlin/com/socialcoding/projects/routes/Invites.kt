package com.socialcoding.projects.routes

import com.socialcoding.user.currentUserID
import com.socialcoding.projects.invitesForUser
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects/invites — projects the signed-in user has a pending invite to. */
val INVITES: suspend RoutingContext.() -> Unit = {
    call.respond(invitesForUser(currentUserID()))
}
