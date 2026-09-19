package com.socialcoding.board.routes

import com.socialcoding.user.requireRole
import com.socialcoding.board.BoardSettings
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Every board-configurable value.
 *
 * GET /api/board/settings
 */
val GET_SETTINGS: suspend RoutingContext.() -> Unit = {
    requireRole()

    call.respond(BoardSettings.config())
}
