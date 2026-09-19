package com.socialcoding.board.routes

import com.socialcoding.board.BoardSettings
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/** The slice of board configuration the site footer needs. */
@Serializable data class FooterResponse(val footerText: String)

/**
 * The footer's meeting line.
 *
 * GET /api/site/footer
 */
val GET_FOOTER: suspend RoutingContext.() -> Unit = {
    call.respond(FooterResponse(BoardSettings.footerText()))
}
