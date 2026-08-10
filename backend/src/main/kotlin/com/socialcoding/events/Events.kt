package com.socialcoding.events

import com.socialcoding.api.db.MappedTable
import com.socialcoding.api.db.SqlTable
import com.socialcoding.api.db.toEntity
import com.socialcoding.people.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@SqlTable
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
    val recurring = bool("recurring").default(false)
    val announce = bool("announce").default(false)
    val announcedAt = long("announced_at").nullable()
    val createdBy = uuid("created_by").references(Users.id)
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
 * @param startsAt When the next occurrence takes place, in epoch ms; drives the calendar.
 * @param location The optional location.
 * @param burrowUrl The optional external Burrow link for the event.
 * @param imageUrl The optional promotional image.
 * @param attendance Whether attendance tracking is enabled for this event.
 * @param recurring Whether the event repeats weekly on [startsAt]'s weekday and time. Recurring
 *   events keep a single row: [RecurringEvents] advances [startsAt] a week at a time once an
 *   occurrence is past, so it always names the next meeting.
 * @param announce Whether to post this event to Discord at noon on the day it happens.
 * @param announcedAt When the event was announced to Discord, in epoch ms, or null if it hasn't
 *   been yet. Set once per occurrence so re-saving an event never reposts it.
 * @param authorName The name of the board member who posted it. Joined from [Users] rather than
 *   stored on [Events], so [toEntity] can't fill it — [getEventsWithAuthor] copies it in.
 * @param createdAt When the event was posted, in epoch ms.
 */
@MappedTable(Events::class)
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
    val recurring: Boolean,
    val announce: Boolean,
    val announcedAt: Long?,
    val authorName: String = "",
    val createdAt: Long,
)

/** Retrieve all events. */
private fun getEvents() = Events.join(Users, JoinType.INNER, Events.createdBy, Users.id).selectAll()

/** Retrieve all events with the author name.. */
private fun ResultRow.getEventsWithAuthor(): Event =
    toEntity<Event>(Events).copy(authorName = this[Users.name])

/** Every event, most recent event date first. */
fun getAllEvents(): List<Event> = transaction {
    getEvents().orderBy(Events.startsAt to SortOrder.DESC).map { it.getEventsWithAuthor() }
}

/** Load a single event by id, or null if it doesn't exist. */
fun getEventByID(id: Long): Event? = transaction {
    getEvents().where { Events.id eq id }.firstOrNull()?.getEventsWithAuthor()
}
