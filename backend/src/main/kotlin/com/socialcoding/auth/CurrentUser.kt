package com.socialcoding.auth

import com.socialcoding.people.Role
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext

/** The signed-in user's id, from the session JWT. Only valid inside `authenticate("session")`. */
fun RoutingContext.currentUserId(): Long = call.principal<JWTPrincipal>()!!.subject!!.toLong()

/** The signed-in user's role, from the session JWT. Only valid inside `authenticate("session")`. */
fun RoutingContext.currentRole(): Role =
    Role.valueOf(call.principal<JWTPrincipal>()!!.payload.getClaim("role").asString())
