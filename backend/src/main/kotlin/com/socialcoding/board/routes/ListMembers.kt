package com.socialcoding.board.routes

import com.socialcoding.api.db.query
import com.socialcoding.api.db.toEntity
import com.socialcoding.people.User
import com.socialcoding.people.Users
import com.socialcoding.user.requireRole
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Every registered user, for managing the board role.
 *
 * GET /api/board/members
 */
val LIST_MEMBERS: suspend RoutingContext.() -> Unit = {
    requireRole()
    
    val users = query {
        Users.selectAll().orderBy(Users.name).map { it.toEntity<User>() }
    }

    call.respond(users)
}
