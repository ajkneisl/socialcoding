package com.socialcoding.projects.routes

import com.socialcoding.board.BoardSettings
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/** GET /api/projects/presentation-dates — the board-set MVP/Final dates milestones inherit. */
val PRESENTATION_DATES: suspend RoutingContext.() -> Unit = {
    call.respond(BoardSettings.presentationDates())
}
