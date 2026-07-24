package com.socialcoding.board.routes

import com.socialcoding.auth.argument
import com.socialcoding.auth.body
import com.socialcoding.auth.requireRole
import com.socialcoding.db.User
import com.socialcoding.db.Users
import com.socialcoding.db.find
import com.socialcoding.db.update
import com.socialcoding.projects.toUuid
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * A request to rename a board member's role.
 *
 * @param title The display name for the role, or null to fall back to "Board".
 */
@Serializable private data class TitleRequest(val title: String? = null)

/** PUT /api/board/members/{id}/title — rename the role a board member holds. */
val SET_TITLE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val targetID = argument("id", String::toUuid)
    val title = body<TitleRequest>().title?.trim()?.take(64)?.ifBlank { null }

    val user = Users.find<User>(targetID)
    user.update(User::title, title).join()
    call.respond(user.copy(title = title))
}
