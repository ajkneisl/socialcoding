package com.socialcoding.projects.routes

import com.socialcoding.user.currentUserID
import com.socialcoding.common.NotFound
import com.socialcoding.projects.approvedProjectExists
import com.socialcoding.projects.toUuidOrNull
import com.socialcoding.projects.toggleLike
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** POST /api/projects/{id}/like — toggle the signed-in user's heart on an approved project. */
val LIKE: suspend RoutingContext.() -> Unit = {
    val userID = currentUserID()
    val projectID = call.parameters["id"]?.toUuidOrNull() ?: throw NotFound("project")
    if (!approvedProjectExists(projectID)) throw NotFound("project")

    call.respond(toggleLike(projectID, userID))
}
