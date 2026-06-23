package com.socialcoding.projects

import com.socialcoding.db.Users
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object ProjectLikes : Table("project_likes") {
    val projectID = uuid("project_id").references(Projects.id)
    val userID = uuid("user_id").references(Users.id)

    // A user may only like a project once, so the pair is the primary key.
    override val primaryKey = PrimaryKey(projectID, userID)
}

/** The like count and the requesting user's like state for a project. */
@Serializable
data class LikeResult(val liked: Boolean, val likes: Long)

/** Whether an approved (publicly listed) project exists. Only those may be liked. */
fun approvedProjectExists(projectID: Uuid): Boolean = transaction {
    Projects.selectAll()
        .where { (Projects.id eq projectID) and (Projects.status eq ProjectStatus.APPROVED) }
        .any()
}

/** Toggles the user's like on a project and returns the new state and count. */
fun toggleLike(projectID: Uuid, userID: Uuid): LikeResult = transaction {
    val already =
        ProjectLikes.selectAll()
            .where { (ProjectLikes.projectID eq projectID) and (ProjectLikes.userID eq userID) }
            .any()

    if (already) {
        ProjectLikes.deleteWhere {
            (ProjectLikes.projectID eq projectID) and (ProjectLikes.userID eq userID)
        }
    } else {
        ProjectLikes.insert {
            it[ProjectLikes.projectID] = projectID
            it[ProjectLikes.userID] = userID
        }
    }

    val likes = ProjectLikes.selectAll().where { ProjectLikes.projectID eq projectID }.count()
    LikeResult(liked = !already, likes = likes)
}

/** Like counts keyed by project id. Must be inside a transaction. */
fun likeCountsFor(projectIDs: Collection<Uuid>): Map<Uuid, Long> {
    if (projectIDs.isEmpty()) return emptyMap()
    return ProjectLikes.selectAll()
        .where { ProjectLikes.projectID inList projectIDs }
        .groupingBy { it[ProjectLikes.projectID] }
        .eachCount()
        .mapValues { it.value.toLong() }
}

/** The subset of [projectIDs] that [userID] has liked. Must be inside a transaction. */
fun likedProjectIds(userID: Uuid, projectIDs: Collection<Uuid>): Set<Uuid> {
    if (projectIDs.isEmpty()) return emptySet()
    return ProjectLikes.selectAll()
        .where { (ProjectLikes.userID eq userID) and (ProjectLikes.projectID inList projectIDs) }
        .map { it[ProjectLikes.projectID] }
        .toSet()
}
