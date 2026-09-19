package com.socialcoding.projects.tasks

import com.socialcoding.api.db.SqlTable
import org.jetbrains.exposed.v1.core.Table

/** A log of deadline reminders already delivered, so none is ever sent twice. */
@SqlTable
object SentReminders : Table("sent_reminders") {
    val projectID = uuid("project_id")
    val taskName = varchar("task_name", 300)
    val dueDate = varchar("due_date", 10)
    val offsetDays = integer("offset_days")

    override val primaryKey = PrimaryKey(projectID, taskName, dueDate, offsetDays)
}
