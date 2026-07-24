package com.socialcoding.auth.routes

import com.socialcoding.auth.Auth
import com.socialcoding.auth.GoogleVerifier
import com.socialcoding.db.Role
import com.socialcoding.db.User
import com.socialcoding.db.Users
import com.socialcoding.db.toUser
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The payload when signing in with Google.
 *
 * @param credential The credential provided by Google.
 */
@Serializable private data class GoogleLoginRequest(val credential: String)

/**
 * The response when signing in.
 *
 * @param token The generated JWT.
 * @param user The details about the user who just signed in.
 */
@Serializable private data class LoginResponse(val token: String, val user: User)

/** POST /api/auth/google — log in with a Google token, creating the user on first sign-in. */
val GOOGLE_LOGIN: suspend RoutingContext.() -> Unit = {
    val identity = GoogleVerifier.verify(call.receive<GoogleLoginRequest>().credential)

    val user = transaction {
        val existing =
            Users.selectAll()
                .where { (Users.googleID eq identity.googleID) or (Users.email eq identity.email) }
                .firstOrNull()

        if (existing == null) {
            // insert new user
            Users.insert {
                it[googleID] = identity.googleID
                it[email] = identity.email
                it[name] = identity.name
                it[avatarUrl] = identity.picture
                it[role] = Role.MEMBER
                it[createdAt] = System.currentTimeMillis()
            }
        } else {
            // update user's google info, pfp may have updated etc
            Users.update({ Users.id eq existing[Users.id] }) {
                it[googleID] = identity.googleID
                it[avatarUrl] = identity.picture
                it[Users.role] = role
            }
        }

        Users.selectAll().where { Users.email eq identity.email }.first().toUser()
    }

    val token = Auth.issue(user.id)
    call.respond(LoginResponse(token, user))
}
