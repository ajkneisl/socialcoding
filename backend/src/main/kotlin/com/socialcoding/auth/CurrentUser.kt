package com.socialcoding.auth

import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.db.Role
import com.socialcoding.db.Users
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** The signed-in user's ID from the session JWT. */
fun RoutingContext.currentUserID(): Long = call.principal<JWTPrincipal>()!!.subject!!.toLong()

/** The signed-in user's role. */
fun RoutingContext.currentRole(): Role {
    val userID = currentUserID()

    val userRow =
        Users.selectAll().where { Users.id eq userID }.singleOrNull()
            ?: throw InvalidAuthorization()

    return userRow[Users.role]
}
