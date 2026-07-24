package com.socialcoding.projects

import com.socialcoding.db.MappedTable
import com.socialcoding.db.MapsTo
import com.socialcoding.db.toEntity
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object ProjectTasks : Table("project_tasks") {
    val id = long("id").autoIncrement()
    val projectID = uuid("project_id").references(Projects.id)
    val name = varchar("name", 300)
    val assigneeIDs = varchar("assignee_ids", 1000).default("[]")
    val dueDate = varchar("due_date", 10).default("")
    val dependsOnIDs = varchar("depends_on_ids", 1000).default("[]")
    val milestone = bool("milestone").default(false)

    override val primaryKey = PrimaryKey(id)
}

/** A task for a [Project]. Both id lists are stored as JSON arrays, so they map via [toEntity]. */
@Serializable
@MappedTable(ProjectTasks::class)
data class ProjectTask(
    val id: Long,
    val name: String,
    val assigneeIds: List<String>,
    val dueDate: String,
    @MapsTo("depends_on_ids") val dependsOn: List<Long>,
    val milestone: Boolean,
)

fun ResultRow.toTask(): ProjectTask = toEntity()
