package com.socialcoding.projects.tasks

import com.socialcoding.api.db.MappedTable
import com.socialcoding.api.db.MapsTo
import kotlinx.serialization.Serializable

/**
 * A task for a [com.socialcoding.projects.models.Project].
 *
 * @param id The ID of the task.
 * @param name The name of the task.
 * @param assigneeIDs The users assigned to the task.
 * @param dueDate The due date.
 * @param dependsOn What tasks this one depends on.
 * @param milestone If this is an MVP / Final task.
 */
@Serializable
@MappedTable(ProjectTasks::class)
data class ProjectTask(
    val id: Long,
    val name: String,
    val assigneeIDs: List<String>,
    val dueDate: String,
    @MapsTo("depends_on_ids") val dependsOn: List<Long>,
    val milestone: Boolean,
)
