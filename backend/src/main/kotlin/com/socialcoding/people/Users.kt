package com.socialcoding.db

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

/** [User] table. */
object Users : Table("users") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val googleID = varchar("google_id", 64).nullable().uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val role = enumerationByName("role", 16, Role::class).default(Role.MEMBER)
    val joinedTerm = varchar("joined_term", 32).nullable()
    val gradYear = integer("grad_year").nullable()
    val github = varchar("github", 255).nullable()
    val linkedin = varchar("linkedin", 255).nullable()
    val website = varchar("website", 512).nullable()
    val company = varchar("company", 255).nullable()
    val title = varchar("title", 64).nullable()
    val listed = bool("listed").default(true)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** A role a user may have. */
enum class Role {
    MEMBER,
    BOARD,
}

/** A signed-in user's record. */
@Serializable
data class User(
    val id: String,
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
    val listed: Boolean = true,
)

fun ResultRow.toUser() =
    User(
        id = this[Users.id].toString(),
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
        listed = this[Users.listed],
    )
