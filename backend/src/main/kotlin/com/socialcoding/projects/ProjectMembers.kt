package com.socialcoding.db

import com.socialcoding.projects.Projects
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table

object ProjectMembers : Table("project_members") {
    val projectID = long("project_id").references(Projects.id)
    val userID = long("user_id").references(Users.id)

    override val primaryKey = PrimaryKey(projectID, userID)
}

@Serializable
data class ProjectMember(val id: Long, val name: String, val avatarUrl: String? = null)
