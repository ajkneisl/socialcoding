package com.socialcoding.board.routes

import com.socialcoding.auth.requireRole
import com.socialcoding.board.BoardSettings
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** PUT /api/board/settings — set the presentation dates inherited by project milestones. */
val SET_SETTINGS: suspend RoutingContext.() -> Unit = {
    requireRole()
    BoardSettings.setPresentationDates(call.receive())
    call.respond(BoardSettings.presentationDates())
}
