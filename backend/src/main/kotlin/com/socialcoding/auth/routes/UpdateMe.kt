package com.socialcoding.auth.routes

import com.socialcoding.auth.currentUserID
import com.socialcoding.db.Users
import com.socialcoding.db.toUser
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Request to update a profile.
 *
 * @param gradYear The user's graduation year.
 * @param github The user's GitHub URL.
 * @param linkedin The user's LinkedIn URL.
 * @param website The user's portfolio.
 * @param company Where the user works.
 * @param listed Whether to show on the public member directory. Omitted leaves it unchanged.
 */
@Serializable
private data class UpdateProfileRequest(
    val gradYear: Int? = null,
    val github: String? = null,
    val linkedin: String? = null,
    val website: String? = null,
    val company: String? = null,
    val listed: Boolean? = null,
)

/** POST /api/me — update the signed-in user's own profile. */
val UPDATE_ME: suspend RoutingContext.() -> Unit = {
    val userId = currentUserID()
    val body = call.receive<UpdateProfileRequest>()

    val user = transaction {
        Users.update({ Users.id eq userId }) {
            it[gradYear] = body.gradYear
            it[github] = body.github
            it[linkedin] = body.linkedin
            it[website] = body.website
            it[company] = body.company
            if (body.listed != null) it[listed] = body.listed
        }
        Users.selectAll().where { Users.id eq userId }.first().toUser()
    }

    call.respond(user)
}
