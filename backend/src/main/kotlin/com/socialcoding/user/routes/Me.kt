package com.socialcoding.user.routes

import com.socialcoding.api.db.find
import com.socialcoding.people.User
import com.socialcoding.people.Users
import com.socialcoding.user.currentUserID
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Get the current user.
 *
 * @route /api/me
 */
val ME: suspend RoutingContext.() -> Unit = {
    val user = Users.find<User>(currentUserID())
    call.respond(user)
}
