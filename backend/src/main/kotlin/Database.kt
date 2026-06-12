package com.socialcoding

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val googleSub = varchar("google_sub", 64).nullable().uniqueIndex()
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
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Projects : Table("projects") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 200)
    val description = text("description")
    val longDescription = text("long_description").nullable()
    val active = bool("active").default(true)
    val repoUrl = varchar("repo_url", 512).nullable()
    val siteUrl = varchar("site_url", 512).nullable()
    val ownerId = long("owner_id").references(Users.id)
    val teamLeadId = long("team_lead_id").references(Users.id).nullable()
    val designDoc = text("design_doc").nullable()
    val status =
        enumerationByName("status", 16, ProjectStatus::class).default(ProjectStatus.PENDING)
    val submittedAt = long("submitted_at")
    val reviewedBy = long("reviewed_by").references(Users.id).nullable()
    val reviewNote = varchar("review_note", 1000).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ProjectMembers : Table("project_members") {
    val projectId = long("project_id").references(Projects.id)
    val userId = long("user_id").references(Users.id)

    override val primaryKey = PrimaryKey(projectId, userId)
}

object ProjectTasks : Table("project_tasks") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val name = varchar("name", 300)
    val assigneeIds = varchar("assignee_ids", 1000).default("")
    val dueDate = varchar("due_date", 10).default("")
    val dependsOnIds = varchar("depends_on_ids", 1000).default("")
    val milestone = bool("milestone").default(false)

    override val primaryKey = PrimaryKey(id)
}

fun String.toIdList(): List<Long> = split(',').mapNotNull { it.trim().toLongOrNull() }

fun ResultRow.toTask() =
    TaskDto(
        id = this[ProjectTasks.id],
        name = this[ProjectTasks.name],
        assigneeIds = this[ProjectTasks.assigneeIds].toIdList(),
        dueDate = this[ProjectTasks.dueDate],
        dependsOn = this[ProjectTasks.dependsOnIds].toIdList(),
        milestone = this[ProjectTasks.milestone],
    )

fun ResultRow.toPerson() =
    PersonDto(
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

fun ResultRow.toProject(ownerName: String) =
    ProjectDto(
        id = this[Projects.id],
        title = this[Projects.title],
        description = this[Projects.description],
        longDescription = this[Projects.longDescription],
        active = this[Projects.active],
        repoUrl = this[Projects.repoUrl],
        siteUrl = this[Projects.siteUrl],
        status = this[Projects.status],
        ownerName = ownerName,
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )

object DatabaseFactory {

    fun init(jdbcUrl: String, driver: String, user: String, password: String) {
        Database.connect(jdbcUrl, driver = driver, user = user, password = password)
        transaction {
            SchemaUtils.create(Users, Projects, ProjectMembers, ProjectTasks)
            // SchemaUtils.create doesn't alter existing tables; bring older databases up to date.
            exec("ALTER TABLE projects ADD COLUMN IF NOT EXISTS team_lead_id BIGINT")
            exec("ALTER TABLE projects ADD COLUMN IF NOT EXISTS design_doc TEXT")
        }
    }
}
