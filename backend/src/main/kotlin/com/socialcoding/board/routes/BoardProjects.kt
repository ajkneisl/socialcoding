package com.socialcoding.board.routes

import com.socialcoding.projects.getPendingProjects
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Projects that are not reviewed yet.
 *
 * GET /api/board/projects
 */
val BOARD_PROJECTS: suspend RoutingContext.() -> Unit = {
    requireRole()

    call.respond(getPendingProjects())
}
