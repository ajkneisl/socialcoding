package com.socialcoding.board.routes

import com.socialcoding.api.db.find
import com.socialcoding.api.db.update
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.people.Role
import com.socialcoding.people.User
import com.socialcoding.people.Users
import com.socialcoding.projects.toUuid
import com.socialcoding.user.argument
import com.socialcoding.user.body
import com.socialcoding.user.currentUserID
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * A request to change a user's role.
 *
 * @param role The role to grant the user.
 */
@Serializable private data class RoleRequest(val role: Role)

/**
 * Promote a member to the board or demote back to member.
 *
 * PUT /api/board/members/{id}/role
 */
val SET_ROLE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val targetID = argument("id", String::toUuid)
    if (targetID == currentUserID()) throw InvalidAuthorization()

    val role = body<RoleRequest>().role
    val user = Users.find<User>(targetID)

    user.update(User::role, role).join()
    call.respond(user.copy(role = role))
}
