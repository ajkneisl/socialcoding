package com.socialcoding.board.routes

import com.socialcoding.api.db.query
import com.socialcoding.board.EmployerDirectory
import com.socialcoding.user.argument
import com.socialcoding.user.body
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * A request to add an employer.
 *
 * @param name The employer's display name.
 */
@Serializable private data class AddEmployerRequest(val name: String)

/**
 * A request to show or hide an employer.
 *
 * @param name The employer's display name, stored if it was only sourced from profiles.
 * @param visible Whether it appears on the public home page.
 */
@Serializable private data class EmployerVisibilityRequest(val name: String, val visible: Boolean)

/**
 * The employers cleared for the home page.
 *
 * GET /api/site/employers
 */
val VISIBLE_EMPLOYERS: suspend RoutingContext.() -> Unit = {
    call.respond(query { EmployerDirectory.visibleNames() })
}

/**
 * Every employer, stored or sourced from member profiles.
 *
 * GET /api/board/employers
 */
val LIST_EMPLOYERS: suspend RoutingContext.() -> Unit = {
    requireRole()
    call.respond(query { EmployerDirectory.list() })
}

/**
 * Add an employer, hidden until the board shows it. Responds with the updated list.
 *
 * POST /api/board/employers
 */
val ADD_EMPLOYER: suspend RoutingContext.() -> Unit = {
    requireRole()
    val name = body<AddEmployerRequest>().name
    call.respond(
        query {
            EmployerDirectory.add(name)
            EmployerDirectory.list()
        }
    )
}

/**
 * Show or hide an employer. Responds with the updated list.
 *
 * PUT /api/board/employers
 */
val SET_EMPLOYER_VISIBILITY: suspend RoutingContext.() -> Unit = {
    requireRole()
    val request = body<EmployerVisibilityRequest>()
    call.respond(
        query {
            EmployerDirectory.setVisible(request.name, request.visible)
            EmployerDirectory.list()
        }
    )
}

/**
 * Remove a stored employer. Responds with the updated list.
 *
 * DELETE /api/board/employers/{key}
 */
val REMOVE_EMPLOYER: suspend RoutingContext.() -> Unit = {
    requireRole()
    val key = argument("key")
    call.respond(
        query {
            EmployerDirectory.remove(key)
            EmployerDirectory.list()
        }
    )
}
