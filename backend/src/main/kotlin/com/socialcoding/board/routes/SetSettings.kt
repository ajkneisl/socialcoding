package com.socialcoding.board.routes

import com.socialcoding.board.BoardConfig
import com.socialcoding.board.BoardSettings
import com.socialcoding.user.body
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Set the milestone dates and the Discord announcement channel.
 *
 * PUT /api/board/settings
 */
val SET_SETTINGS: suspend RoutingContext.() -> Unit = {
    requireRole()
    BoardSettings.setConfig(body<BoardConfig>())
    call.respond(BoardSettings.config())
}
