package com.socialcoding.projects

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** Task as submitted by the client; dependencies reference indices into the submitted list. */
@Serializable
data class TaskInput(
    val name: String,
    val assigneeIds: List<String> = emptyList(),
    val dueDate: String = "",
    val dependsOn: List<Int> = emptyList(),
    val milestone: Boolean = false,
)

/** Parses a user id string into a [Uuid], or null if it isn't a valid UUID. */
fun String.toUuidOrNull(): Uuid? = runCatching { Uuid.parse(trim()) }.getOrNull()

/** Decodes the comma-separated task-row-id column on ProjectTasks. */
fun String.toIDList(): List<Long> = split(',').mapNotNull { it.trim().toLongOrNull() }

/** Decodes the comma-separated user-id (UUID) column on ProjectTasks. */
fun String.toUserIdList(): List<Uuid> = split(',').mapNotNull { it.toUuidOrNull() }

