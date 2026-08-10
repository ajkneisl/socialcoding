package com.socialcoding.user.routes

import com.socialcoding.api.db.find
import com.socialcoding.api.db.query
import com.socialcoding.user.body
import com.socialcoding.user.currentUserID
import com.socialcoding.people.User
import com.socialcoding.people.Users
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Request to update a profile.
 *
 * @param gradYear The user's graduation year.
 * @param github The user's GitHub URL.
 * @param linkedin The user's LinkedIn URL.
 * @param website The user's portfolio.
 * @param company Where the user works.
 * @param listed Whether to show on the public member directory. Omitted leaves
 *   it unchanged.
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

/**
 * Update the signed in user's profile.
 *
 * @method POST
 * @route /api/me
 */
val UPDATE_ME: suspend RoutingContext.() -> Unit = {
    val userID = currentUserID()
    val body = body<UpdateProfileRequest>()

    query {
        Users.update({ Users.id eq userID }) {
            it[gradYear] = body.gradYear
            it[github] = body.github
            it[linkedin] = body.linkedin
            it[website] = body.website
            it[company] = body.company
            if (body.listed != null) it[listed] = body.listed
        }
    }

    call.respond(Users.find<User>(userID))
}
