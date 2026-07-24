package com.socialcoding.board.routes

import com.socialcoding.auth.requireRole
import com.socialcoding.projects.pendingProjects
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/board/projects — the pending review queue. */
val BOARD_PROJECTS: suspend RoutingContext.() -> Unit = {
    requireRole()
    call.respond(pendingProjects())
}
