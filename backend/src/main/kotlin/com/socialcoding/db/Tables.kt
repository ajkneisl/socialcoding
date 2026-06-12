package com.socialcoding.db

import com.socialcoding.people.Role
import com.socialcoding.projects.ProjectStatus
import org.jetbrains.exposed.v1.core.Table

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
