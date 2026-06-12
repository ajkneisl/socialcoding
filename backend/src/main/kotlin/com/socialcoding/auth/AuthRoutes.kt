package com.socialcoding.auth

import com.socialcoding.common.ApiError
import com.socialcoding.db.Users
import com.socialcoding.people.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

@Serializable data class GoogleLoginRequest(val credential: String)

@Serializable data class LoginResponse(val token: String, val user: UserDto)

/** The signed-in user's own record; includes the email, which PersonDto deliberately omits. */
@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val joinedTerm: String?,
    val gradYear: Int?,
    val github: String?,
    val linkedin: String?,
    val website: String?,
    val company: String?,
    val title: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class UpdateProfileRequest(
    val joinedTerm: String? = null,
    val gradYear: Int? = null,
    val github: String? = null,
    val linkedin: String? = null,
    val website: String? = null,
    val company: String? = null,
)

fun ResultRow.toUser() =
    UserDto(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        role = this[Users.role],
        joinedTerm = this[Users.joinedTerm],
        gradYear = this[Users.gradYear],
        github = this[Users.github],
        linkedin = this[Users.linkedin],
        website = this[Users.website],
        company = this[Users.company],
        title = this[Users.title],
        avatarUrl = this[Users.avatarUrl],
    )

/** Sign-in and the signed-in user's own profile (`/auth/google`, `/me`). */
fun Route.authRoutes(google: GoogleVerifier, sessions: SessionTokens, boardEmails: Set<String>) {
    post("/auth/google") {
        val identity = google.verify(call.receive<GoogleLoginRequest>().credential)

        val user = transaction {
            val existing =
                Users.selectAll()
                    .where {
                        (Users.googleSub eq identity.sub) or (Users.email eq identity.email)
                    }
                    .firstOrNull()
            // BOARD_EMAILS grants board access; seeded/claimed board members keep theirs.
            val role =
                when {
                    identity.email.lowercase() in boardEmails -> Role.BOARD
                    existing != null -> existing[Users.role]
                    else -> Role.MEMBER
                }
            if (existing == null) {
                Users.insert {
                    it[googleSub] = identity.sub
                    it[email] = identity.email
                    it[name] = identity.name
                    it[avatarUrl] = identity.picture
                    it[Users.role] = role
                    it[createdAt] = System.currentTimeMillis()
                }
            } else {
                // Claims seeded rows and keeps profile/role in sync with Google + board list.
                Users.update({ Users.id eq existing[Users.id] }) {
                    it[googleSub] = identity.sub
                    it[avatarUrl] = identity.picture
                    it[Users.role] = role
                }
            }
            Users.selectAll().where { Users.email eq identity.email }.first().toUser()
        }

        call.respond(LoginResponse(sessions.issue(user.id, user.role), user))
    }

    authenticate("session") {
        get("/me") {
            val userId = currentUserId()
            val user = transaction {
                Users.selectAll().where { Users.id eq userId }.firstOrNull()?.toUser()
            }
            if (user == null) call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
            else call.respond(user)
        }

        post("/me") {
            val userId = currentUserId()
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
