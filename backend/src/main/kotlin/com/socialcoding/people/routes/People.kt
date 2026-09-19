package com.socialcoding.people.routes

import com.socialcoding.api.db.toEntity
import com.socialcoding.people.User
import com.socialcoding.people.Users
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Get all public-facing people.
 *
 * @method GET
 * @route /api/people.
 */
val PEOPLE: suspend RoutingContext.() -> Unit = {
    val people = transaction {
        Users.selectAll()
            .where { Users.listed eq true }
            .orderBy(Users.name)
            .map { it.toEntity<User>().copy(email = "") }
    }

    call.respond(people)
}
