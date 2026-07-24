package com.socialcoding.board.routes

import com.socialcoding.auth.requireRole
import com.socialcoding.board.BoardSettings
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/board/settings — the board-wide presentation dates. */
val GET_SETTINGS: suspend RoutingContext.() -> Unit = {
    requireRole()
    call.respond(BoardSettings.presentationDates())
}
