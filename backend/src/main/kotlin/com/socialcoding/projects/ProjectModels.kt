package com.socialcoding.projects

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

fun String.toUuid(): Uuid = Uuid.parse(trim())

/** Encodes user ids as the JSON array of uuid strings stored in the `assignee_ids` column. */
fun List<Uuid>.toIdJson(): String = Json.encodeToString(map { it.toString() })

/** Decodes the JSON `assignee_ids` column back into user ids. */
fun String.toUserIdList(): List<Uuid> =
    runCatching { Json.decodeFromString<List<String>>(this) }
        .getOrDefault(emptyList())
        .mapNotNull { it.toUuidOrNull() }

