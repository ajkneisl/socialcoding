package com.socialcoding.people

import com.socialcoding.db.Role
import com.socialcoding.db.Users
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Public profile shown on the People page; deliberately omits the email. */
@Serializable
data class Person(
    val id: Long,
    val name: String,
    val joinedTerm: String?,
    val gradYear: Int?,
    val github: String?,
    val linkedin: String?,
    val website: String?,
    val company: String?,
    val role: Role,
    val title: String? = null,
    val avatarUrl: String? = null,
)

fun ResultRow.toPerson() =
    Person(
        id = this[Users.id],
        name = this[Users.name],
        joinedTerm = this[Users.joinedTerm],
        gradYear = this[Users.gradYear],
        github = this[Users.github],
        linkedin = this[Users.linkedin],
        website = this[Users.website],
        company = this[Users.company],
        role = this[Users.role],
        title = this[Users.title],
        avatarUrl = this[Users.avatarUrl],
    )

/** The public member directory. */
fun Route.peopleRoutes() {
    get("/people") {
        val people = transaction { Users.selectAll().orderBy(Users.name).map { it.toPerson() } }
        call.respond(people)
    }
}
