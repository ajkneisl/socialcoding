package com.socialcoding.projects.tasks

import com.socialcoding.api.db.SqlTable
import com.socialcoding.projects.Projects
import org.jetbrains.exposed.v1.core.Table

/** Tasks for a project. */
@SqlTable
object ProjectTasks : Table("project_tasks") {
    /** The unique ID of the project task. */
    val id = long("id").autoIncrement()

    /** The ID of the project this task corresponds to. */
    val projectID = uuid("project_id").references(Projects.id)

    /** [ProjectTask.name] */
    val name = varchar("name", 300)

    /** [ProjectTask.assigneeIDs] */
    val assigneeIDs = varchar("assignee_ids", 1000).default("[]")

    /** [ProjectTask.dueDate] */
    val dueDate = varchar("due_date", 10).default("")

    /** [ProjectTask.dependsOn] */
    val dependsOnIDs = varchar("depends_on_ids", 1000).default("[]")

    /** [ProjectTask.milestone] */
    val milestone = bool("milestone").default(false)

    override val primaryKey = PrimaryKey(id)
}
