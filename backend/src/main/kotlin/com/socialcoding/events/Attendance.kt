package com.socialcoding.events

import com.socialcoding.api.db.SqlTable
import com.socialcoding.api.db.query
import com.socialcoding.people.Users
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

const val ATTENDANCE_OPENS_MS = -60 * 60 * 1000L // 1 hour before
const val ATTENDANCE_CLOSES_MS = 2 * 60 * 60 * 1000L // 2 hours after

@SqlTable
object EventAttendance : Table("event_attendance") {
    val eventID = long("event_id").references(Events.id)
    val userID = uuid("user_id").references(Users.id)
    val recordedAt = long("recorded_at")

    override val primaryKey = PrimaryKey(eventID, userID)
}

/**
 * The attendance total of a finished occurrence of a recurring event.
 *
 * A recurring event is a single [Events] row whose date rolls forward every week, so its
 * [EventAttendance] rows can't accumulate: a member who checked in last week would be locked out of
 * this week's meeting by the (event, user) primary key. [com.socialcoding.events.RecurringEvents]
 * therefore banks each finished occurrence's headcount here and clears the check-ins, leaving the
 * next meeting a clean slate while analytics keep the history.
 */
@SqlTable
object EventOccurrences : Table("event_occurrences") {
    val eventID = long("event_id").references(Events.id)
    val startsAt = long("starts_at")
    val attendees = long("attendees")

    override val primaryKey = PrimaryKey(eventID, startsAt)
}

/** An attendee. */
@Serializable
data class Attendee(val name: String, val email: String, val recordedAt: Long)

/** Per-event attendance totals. */
@Serializable
data class EventAttendanceSummary(
    val eventId: Long,
    val title: String,
    val startsAt: Long,
    val attendees: Long,
)

/** The outcome of a check-in attempt. */
enum class AttendOutcome {
    RECORDED,
    ALREADY,
}

/** Records the user's attendance for an event. */
suspend fun recordAttendance(eventID: Long, userID: Uuid): AttendOutcome =
    query {
        val already =
            EventAttendance.selectAll()
                .where {
                    (EventAttendance.eventID eq eventID) and
                        (EventAttendance.userID eq userID)
                }
                .any()

        if (already) return@query AttendOutcome.ALREADY

        EventAttendance.insert {
            it[EventAttendance.eventID] = eventID
            it[EventAttendance.userID] = userID
            it[recordedAt] = System.currentTimeMillis()
        }

        AttendOutcome.RECORDED
    }

/** How many distinct users attended an event. */
suspend fun getAttendeeCount(eventID: Long): Long = query {
    EventAttendance.selectAll()
        .where { EventAttendance.eventID eq eventID }
        .count()
}

/** Every attendee of an event, earliest check-in first. */
suspend fun getEventAttendees(eventID: Long): List<Attendee> = query {
    EventAttendance.join(
            Users,
            JoinType.INNER,
            EventAttendance.userID,
            Users.id,
        )
        .selectAll()
        .where { EventAttendance.eventID eq eventID }
        .orderBy(EventAttendance.recordedAt to SortOrder.ASC)
        .map {
            Attendee(
                it[Users.name],
                it[Users.email],
                it[EventAttendance.recordedAt],
            )
        }
}

/**
 * Attendance totals for every event with tracking enabled, most recent first. A recurring event
 * contributes one entry per meeting: its upcoming occurrence plus every occurrence already banked in
 * [EventOccurrences].
 */
suspend fun getAttendanceSummary(): List<EventAttendanceSummary> {
    val upcoming =
        getAllEvents().filter { it.attendance }.map {
            EventAttendanceSummary(
                it.id,
                it.title,
                it.startsAt,
                getAttendeeCount(it.id),
            )
        }

    return (upcoming + pastOccurrences()).sortedByDescending { it.startsAt }
}

/** Banked totals for occurrences of recurring events that have already happened. */
private suspend fun pastOccurrences(): List<EventAttendanceSummary> = query {
    EventOccurrences.join(
            Events,
            JoinType.INNER,
            EventOccurrences.eventID,
            Events.id,
        )
        .selectAll()
        .map {
            EventAttendanceSummary(
                it[EventOccurrences.eventID],
                it[Events.title],
                it[EventOccurrences.startsAt],
                it[EventOccurrences.attendees],
            )
        }
}
