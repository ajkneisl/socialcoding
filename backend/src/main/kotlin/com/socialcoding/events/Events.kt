package com.socialcoding.events

import com.socialcoding.db.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 200)
    val summary = text("summary")
    val body = text("body")
    val startsAt = long("starts_at")
    val location = varchar("location", 255).nullable()
    val burrowUrl = varchar("burrow_url", 512).nullable()
    val imageUrl = varchar("image_url", 512).nullable()
    val attendance = bool("attendance").default(false)
    val createdBy = long("created_by").references(Users.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * A community event or update shown on the Events page.
 *
 * @param id The unique ID of the event.
 * @param title The event title.
 * @param summary A short blurb shown in the list and before "Read more".
 * @param body The full write-up revealed by "Read more".
 * @param startsAt When the event takes place, in epoch ms; drives the calendar.
 * @param location The optional location.
 * @param burrowUrl The optional external Burrow link for the event.
 * @param imageUrl The optional promotional image.
 * @param attendance Whether attendance tracking is enabled for this event.
 * @param authorName The name of the board member who posted it.
 * @param createdAt When the event was posted, in epoch ms.
 */
@Serializable
data class Event(
    val id: Long,
    val title: String,
    val summary: String,
    val body: String,
    val startsAt: Long,
    val location: String?,
    val burrowUrl: String?,
    val imageUrl: String?,
    val attendance: Boolean,
    val authorName: String,
    val createdAt: Long,
)

/** Convert a joined [ResultRow] into an [Event]. */
fun ResultRow.toEvent() =
    Event(
        id = this[Events.id],
        title = this[Events.title],
        summary = this[Events.summary],
        body = this[Events.body],
        startsAt = this[Events.startsAt],
        location = this[Events.location],
        burrowUrl = this[Events.burrowUrl],
        imageUrl = this[Events.imageUrl],
        attendance = this[Events.attendance],
        authorName = this[Users.name],
        createdAt = this[Events.createdAt],
    )

private fun eventsWithAuthors() =
    Events.join(Users, JoinType.INNER, Events.createdBy, Users.id).selectAll()

/** Every event, most recent event date first. */
fun listEvents(): List<Event> = transaction {
    eventsWithAuthors().orderBy(Events.startsAt to SortOrder.DESC).map { it.toEvent() }
}

/** Load a single event by id, or null if it doesn't exist. */
fun eventById(id: Long): Event? = transaction {
    eventsWithAuthors().where { Events.id eq id }.firstOrNull()?.toEvent()
}
