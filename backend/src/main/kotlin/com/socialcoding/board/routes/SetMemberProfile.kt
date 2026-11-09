package com.socialcoding.board.routes

import com.socialcoding.projects.toUuid
import com.socialcoding.user.argument
import com.socialcoding.user.body
import com.socialcoding.user.requireRole
import com.socialcoding.user.routes.UpdateProfileRequest
import com.socialcoding.user.routes.updateProfile
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Edit a member's profile details on their behalf.
 *
 * PUT /api/board/members/{id}/profile
 */
val SET_MEMBER_PROFILE: suspend RoutingContext.() -> Unit = {
    requireRole()

    val targetID = argument("id", String::toUuid)
    call.respond(updateProfile(targetID, body<UpdateProfileRequest>()))
}
