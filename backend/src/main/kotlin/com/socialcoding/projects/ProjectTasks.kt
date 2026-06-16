package com.socialcoding.projects

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

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

/**
 * A task for a [Project].
 */
@Serializable
data class ProjectTask(
    val id: Long,
    val name: String,
    val assigneeIds: List<Long>,
    val dueDate: String,
    val dependsOn: List<Long>,
    val milestone: Boolean,
)

fun ResultRow.toTask() =
    ProjectTask(
        id = this[ProjectTasks.id],
        name = this[ProjectTasks.name],
        assigneeIds = this[ProjectTasks.assigneeIds].toIdList(),
        dueDate = this[ProjectTasks.dueDate],
        dependsOn = this[ProjectTasks.dependsOnIds].toIdList(),
        milestone = this[ProjectTasks.milestone],
    )