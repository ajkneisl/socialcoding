package com.socialcoding.board.routes

import com.socialcoding.auth.argument
import com.socialcoding.auth.body
import com.socialcoding.auth.currentUserID
import com.socialcoding.auth.requireRole
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.db.Role
import com.socialcoding.db.User
import com.socialcoding.db.Users
import com.socialcoding.db.find
import com.socialcoding.db.update
import com.socialcoding.projects.toUuid
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * A request to change a user's role.
 *
 * @param role The role to grant the user.
 */
@Serializable private data class RoleRequest(val role: Role)

/** PUT /api/board/members/{id}/role — promote a member to the board or demote back to member. */
val SET_ROLE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val targetID = argument("id", String::toUuid)
    if (targetID == currentUserID()) throw InvalidAuthorization()

    val role = body<RoleRequest>().role
    val user = Users.find<User>(targetID)

    user.update(User::role, role).join()
    call.respond(user.copy(role = role))
}
