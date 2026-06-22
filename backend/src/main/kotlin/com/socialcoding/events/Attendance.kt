package com.socialcoding.events

import com.socialcoding.db.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Attendance window opens this long after an event starts (negative = before). */
const val ATTENDANCE_OPENS_MS = -60 * 60 * 1000L // 1 hour before

/** Attendance window closes this long after an event starts. */
const val ATTENDANCE_CLOSES_MS = 2 * 60 * 60 * 1000L // 2 hours after

object EventAttendance : Table("event_attendance") {
    val id = long("id").autoIncrement()
    val eventID = long("event_id").references(Events.id)
    val userID = long("user_id").references(Users.id)
    val recordedAt = long("recorded_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // A user may only be counted once per event.
        uniqueIndex(eventID, userID)
    }
}

/** A single attendee of an event, for the board export. */
@Serializable
data class Attendee(val name: String, val email: String, val recordedAt: Long)

/** Per-event attendance totals, for the analytics tab. */
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

/** Records the user's attendance for an event, idempotently. Must be within the window. */
fun recordAttendance(eventID: Long, userID: Long): AttendOutcome = transaction {
    val already =
        EventAttendance.selectAll()
            .where { (EventAttendance.eventID eq eventID) and (EventAttendance.userID eq userID) }
            .any()
    if (already) return@transaction AttendOutcome.ALREADY

    EventAttendance.insert {
        it[EventAttendance.eventID] = eventID
        it[EventAttendance.userID] = userID
        it[recordedAt] = System.currentTimeMillis()
    }
    AttendOutcome.RECORDED
}

/** How many distinct users attended an event. */
fun attendeeCount(eventID: Long): Long = transaction {
    EventAttendance.selectAll().where { EventAttendance.eventID eq eventID }.count()
}

/** Every attendee of an event, earliest check-in first. */
fun attendeesOf(eventID: Long): List<Attendee> = transaction {
    EventAttendance.join(Users, JoinType.INNER, EventAttendance.userID, Users.id)
        .selectAll()
        .where { EventAttendance.eventID eq eventID }
        .orderBy(EventAttendance.recordedAt to SortOrder.ASC)
        .map { Attendee(it[Users.name], it[Users.email], it[EventAttendance.recordedAt]) }
}

/** Attendance totals for every event with tracking enabled, most recent first. */
fun attendanceSummary(): List<EventAttendanceSummary> =
    listEvents()
        .filter { it.attendance }
        .map { EventAttendanceSummary(it.id, it.title, it.startsAt, attendeeCount(it.id)) }
