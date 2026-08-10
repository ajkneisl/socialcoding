package com.socialcoding.user

import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.common.MalformedBody
import com.socialcoding.common.invalidArguments
import com.socialcoding.common.missingArguments
import com.socialcoding.people.Role
import com.socialcoding.people.Users
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.RoutingContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun <T> RoutingContext.argument(name: String, transform: String.() -> T): T {
    val rawValue = argument(name)

    return runCatching { transform(rawValue) }.getOrNull()
        ?: throw invalidArguments(name to rawValue)
}

fun RoutingContext.argument(name: String): String {
    return call.parameters[name] ?: throw missingArguments(name)
}

suspend inline fun <reified T : Any> RoutingContext.body(): T {
    val result = runCatching { call.receive<T>() }
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }

    return result.getOrNull() ?: throw MalformedBody()
}

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

/** Require the signed-in user's role to be [role]. */
fun RoutingContext.requireRole(role: Role = Role.BOARD) {
    if (currentRole() != role) throw InvalidAuthorization()
}
