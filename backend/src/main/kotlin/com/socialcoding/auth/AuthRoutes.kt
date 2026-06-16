package com.socialcoding.auth

import com.socialcoding.common.NotFound
import com.socialcoding.db.Role
import com.socialcoding.db.User
import com.socialcoding.db.Users
import com.socialcoding.db.toUser
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Sign-in and the signed-in user's own profile (`/auth/google`, `/me`). */
fun Route.authRoutes() {
    /**
     * The payload when signing in with Google.
     *
     * @param credential The credential provided by Google.
     */
    @Serializable data class GoogleLoginRequest(val credential: String)

    /**
     * The response when signing in.
     *
     * @param token The generated JWT.
     * @param user The details about the user who just signed in.
     */
    @Serializable data class LoginResponse(val token: String, val user: User)

    // POST /api/auth/google
    // login with a google token
    post("/auth/google") {
        val identity = GoogleVerifier.verify(call.receive<GoogleLoginRequest>().credential)

        val user = transaction {
            val existing =
                Users.selectAll()
                    .where {
                        (Users.googleID eq identity.googleID) or (Users.email eq identity.email)
                    }
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

    authenticate("session") {
        // POST /api/me
        // get the user's own information
        get("/me") {
            val userId = currentUserID()
            val user =
                transaction {
                    Users.selectAll().where { Users.id eq userId }.firstOrNull()?.toUser()
                } ?: throw NotFound("user")

            call.respond(user)
        }

        /**
         * Request to update a profile.
         *
         * @param joinedTerm The term the user joined.
         * @param gradYear The user's graduation year.
         * @param github The user's GitHub URL.
         * @param linkedin The user's LinkedIn URL.
         * @param website The user's portfolio.
         * @param company Where the user works.
         */
        @Serializable
        data class UpdateProfileRequest(
            val joinedTerm: String? = null,
            val gradYear: Int? = null,
            val github: String? = null,
            val linkedin: String? = null,
            val website: String? = null,
            val company: String? = null,
        )

        // POST /api/me
        // update the user's own information.
        post("/me") {
            val userId = currentUserID()
            val body = call.receive<UpdateProfileRequest>()

            val user = transaction {
                Users.update({ Users.id eq userId }) {
                    it[joinedTerm] = body.joinedTerm
                    it[gradYear] = body.gradYear
                    it[github] = body.github
                    it[linkedin] = body.linkedin
                    it[website] = body.website
                    it[company] = body.company
                }
                Users.selectAll().where { Users.id eq userId }.first().toUser()
            }

            call.respond(user)
        }
    }
}
