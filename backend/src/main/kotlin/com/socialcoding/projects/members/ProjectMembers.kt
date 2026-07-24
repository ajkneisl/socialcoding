package com.socialcoding.projects

import com.socialcoding.db.MappedTable
import com.socialcoding.db.Users
import com.socialcoding.projects.members.models.MemberStatus
import org.jetbrains.exposed.v1.core.Table

object ProjectMembers : Table("project_members") {
    val projectID = uuid("project_id").references(Projects.id)
    val userID = uuid("user_id").references(Users.id)

    // Existing rows predate invites, so they default to ACCEPTED; new invitees are inserted PENDING.
    val status =
        enumerationByName("status", 16, MemberStatus::class).default(MemberStatus.ACCEPTED)

    override val primaryKey = PrimaryKey(projectID, userID)
}

/**
 * A row of [ProjectMembers].
 */
@MappedTable(ProjectMembers::class)
data class ProjectMembership(
    val projectID: String,
    val userID: String,
    val status: MemberStatus,
)
