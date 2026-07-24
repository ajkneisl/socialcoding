package com.socialcoding.auth.routes

import com.socialcoding.auth.currentUserID
import com.socialcoding.common.NotFound
import com.socialcoding.db.Users
import com.socialcoding.db.toUser
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** GET /api/me — the signed-in user's own record. */
val ME: suspend RoutingContext.() -> Unit = {
    val userId = currentUserID()
    val user =
        transaction { Users.selectAll().where { Users.id eq userId }.firstOrNull()?.toUser() }
            ?: throw NotFound("user")

    call.respond(user)
}
