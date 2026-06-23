package com.socialcoding.auth

import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.db.Role
import com.socialcoding.db.Users
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The signed-in user's ID from the session JWT. */
fun RoutingContext.currentUserID(): Uuid = Uuid.parse(call.principal<JWTPrincipal>()!!.subject!!)

/** The signed-in user's ID, or null on an optionally-authenticated route with no valid token. */
fun RoutingContext.optionalUserID(): Uuid? =
    call.principal<JWTPrincipal>()?.subject?.let { Uuid.parse(it) }

/** The signed-in user's role. */
fun RoutingContext.currentRole(): Role {
    val userID = currentUserID()

    val userRow = transaction {
        Users.selectAll().where { Users.id eq userID }.singleOrNull()
            ?: throw InvalidAuthorization()
    }

    return userRow[Users.role]
}
