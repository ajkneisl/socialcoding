package com.socialcoding.projects

import kotlinx.serialization.Serializable

/** Task as submitted by the client; dependencies reference indices into the submitted list. */
@Serializable
data class TaskInput(
    val name: String,
    val assigneeIds: List<Long> = emptyList(),
    val dueDate: String = "",
    val dependsOn: List<Int> = emptyList(),
    val milestone: Boolean = false,
)

/** Decodes the comma-separated id columns on ProjectTasks. */
fun String.toIdList(): List<Long> = split(',').mapNotNull { it.trim().toLongOrNull() }

