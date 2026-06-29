package com.socialcoding.db

import com.socialcoding.projects.Projects
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table

object ProjectMembers : Table("project_members") {
    val projectID = uuid("project_id").references(Projects.id)
    val userID = uuid("user_id").references(Users.id)

    // Existing rows predate invites, so they default to ACCEPTED; new invitees are inserted PENDING.
    val status =
        enumerationByName("status", 16, MemberStatus::class).default(MemberStatus.ACCEPTED)

    override val primaryKey = PrimaryKey(projectID, userID)
}

/** Whether a member has accepted their project invite or is still pending a decision. */
enum class MemberStatus {
    PENDING,
    ACCEPTED,
}

@Serializable
data class ProjectMember(val id: String, val name: String, val avatarUrl: String? = null)
