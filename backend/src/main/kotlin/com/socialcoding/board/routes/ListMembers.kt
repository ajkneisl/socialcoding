package com.socialcoding.board.routes

import com.socialcoding.auth.requireRole
import com.socialcoding.db.Users
import com.socialcoding.db.query
import com.socialcoding.db.toUser
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.jdbc.selectAll

/** GET /api/board/members — every registered user, for managing the board role. */
val LIST_MEMBERS: suspend RoutingContext.() -> Unit = {
    requireRole()
    val users = query { Users.selectAll().orderBy(Users.name).map { it.toUser() } }
    call.respond(users)
}
