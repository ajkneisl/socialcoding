package com.socialcoding.people.routes

import com.socialcoding.db.Users
import com.socialcoding.people.models.Person
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private fun ResultRow.toPerson() =
    Person(
        id = this[Users.id].toString(),
        name = this[Users.name],
        gradYear = this[Users.gradYear],
        github = this[Users.github],
        linkedin = this[Users.linkedin],
        website = this[Users.website],
        company = this[Users.company],
        role = this[Users.role],
        title = this[Users.title],
        avatarUrl = this[Users.avatarUrl],
    )

/** GET /api/people — the public member directory, listed members only. */
val PEOPLE: suspend RoutingContext.() -> Unit = {
    val people = transaction {
        Users.selectAll().where { Users.listed eq true }.orderBy(Users.name).map { it.toPerson() }
    }
    call.respond(people)
}
