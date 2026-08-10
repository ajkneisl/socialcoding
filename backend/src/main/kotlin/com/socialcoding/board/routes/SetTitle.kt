package com.socialcoding.board.routes

import com.socialcoding.api.db.find
import com.socialcoding.api.db.update
import com.socialcoding.people.User
import com.socialcoding.people.Users
import com.socialcoding.projects.toUuid
import com.socialcoding.user.argument
import com.socialcoding.user.body
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * A request to rename a board member's role.
 *
 * @param title The display name for the role, or null to fall back to "Board".
 */
@Serializable private data class TitleRequest(val title: String? = null)

/**
 * Rename the role a board member holds.
 *
 * PUT /api/board/members/{id}/title
 */
val SET_TITLE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val targetID = argument("id", String::toUuid)
    val title = body<TitleRequest>().title?.trim()?.take(64)?.ifBlank { null }

    val user = Users.find<User>(targetID)
    user.update(User::title, title).join()
    call.respond(user.copy(title = title))
}
