package com.socialcoding.events

import com.socialcoding.TestDatabase
import com.socialcoding.people.Users
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class RecurringEventsTest {
    /** A fixed zone keeps epoch-ms expectations stable wherever the suite runs. */
    private val zone = ZoneId.of("America/Chicago")

    /** Monday, 2026-08-03. */
    private val monday = LocalDate.parse("2026-08-03")

    private val emailCounter = AtomicInteger()

    @BeforeTest fun clean() = TestDatabase.reset()

    // --- Next-occurrence arithmetic
    // ----------------------------------------------------

    @Test
    fun `an occurrence a week old rolls to the same weekday and time`() {
        val lastWednesday = at("2026-07-29T18:00")
        val next =
            RecurringEvents.nextOccurrence(lastWednesday, monday, zone)

        assertEquals(at("2026-08-05T18:00"), next)
        assertEquals("18:00", localTime(next))
    }

    @Test
    fun `a long-stale occurrence catches all the way up in one roll`() {
        // Nine weeks behind: the roll should land on the first Wednesday not
        // before `monday`, not merely one week on.
        val next =
            RecurringEvents.nextOccurrence(at("2026-06-03T18:00"), monday, zone)

        assertEquals(at("2026-08-05T18:00"), next)
    }

    @Test
    fun `an occurrence earlier today is treated as still current`() {
        // The roll only considers events from before today, so a same-day
        // start is left where it is.
        val thisMorning = at("2026-08-03T09:00")

        assertEquals(
            thisMorning,
            RecurringEvents.nextOccurrence(thisMorning, monday, zone),
        )
    }

    @Test
    fun `wall-clock time survives a daylight-saving change`() {
        // 2026-10-28 is before the US fall-back (2026-11-01); rolling into
        // November must keep 18:00 local rather than drifting to 17:00.
        val beforeShift = at("2026-10-28T18:00")
        val next =
            RecurringEvents.nextOccurrence(
                beforeShift,
                LocalDate.parse("2026-11-03"),
                zone,
            )

        assertEquals("18:00", localTime(next))
        assertEquals(LocalDate.parse("2026-11-04"), localDate(next))
    }

    // --- The daily roll
    // ----------------------------------------------------------------

    @Test
    fun `the roll advances past recurring events and leaves the rest alone`() =
        runBlocking {
            val author = seedUser()
            val stale =
                seedEvent(author, at("2026-07-29T18:00"), recurring = true)
            val upcoming =
                seedEvent(author, at("2026-08-05T18:00"), recurring = true)
            val oneOff =
                seedEvent(author, at("2026-07-29T18:00"), recurring = false)

            assertEquals(1, RecurringEvents.rollForward(monday, zone))

            assertEquals(at("2026-08-05T18:00"), startsAt(stale))
            assertEquals(
                at("2026-08-05T18:00"),
                startsAt(upcoming),
                "a future occurrence isn't touched",
            )
            assertEquals(
                at("2026-07-29T18:00"),
                startsAt(oneOff),
                "a one-off event never moves",
            )
        }

    @Test
    fun `rolling twice in a day is a no-op the second time`() = runBlocking {
        val event =
            seedEvent(seedUser(), at("2026-07-29T18:00"), recurring = true)

        RecurringEvents.rollForward(monday, zone)
        assertEquals(
            0,
            RecurringEvents.rollForward(monday, zone),
            "nothing is left behind today",
        )
        assertEquals(at("2026-08-05T18:00"), startsAt(event))
    }

    @Test
    fun `a rolled occurrence banks its headcount and frees up check-in`() =
        runBlocking {
            val author = seedUser()
            val met = at("2026-07-29T18:00")
            val event = seedEvent(author, met, recurring = true, attendance = true)
            val attendee = seedUser()
            seedAttendance(event, author)
            seedAttendance(event, attendee)

            RecurringEvents.rollForward(monday, zone)

            assertEquals(
                listOf(met to 2L),
                occurrences(event),
                "last week's total is kept",
            )
            assertEquals(
                0,
                getAttendeeCount(event),
                "check-ins are cleared for the next meeting",
            )

            // The same member can now check in again for the new occurrence.
            assertEquals(
                AttendOutcome.RECORDED,
                recordAttendance(event, attendee),
            )
        }

    @Test
    fun `analytics report every meeting of a recurring event`() = runBlocking {
        val author = seedUser()
        val event =
            seedEvent(
                author,
                at("2026-07-29T18:00"),
                recurring = true,
                attendance = true,
            )
        seedAttendance(event, author)

        RecurringEvents.rollForward(monday, zone)
        seedAttendance(event, seedUser())

        val summary = getAttendanceSummary()
        assertEquals(2, summary.size, "one entry per meeting")
        assertTrue(
            summary.all { it.eventId == event },
            "both entries belong to the same event",
        )
        assertEquals(
            listOf(at("2026-08-05T18:00"), at("2026-07-29T18:00")),
            summary.map { it.startsAt },
            "most recent meeting first",
        )
        assertEquals(listOf(1L, 1L), summary.map { it.attendees })
    }

    @Test
    fun `rolling forward re-arms the announcement for the next occurrence`() =
        runBlocking {
            val event =
                seedEvent(
                    seedUser(),
                    at("2026-07-29T18:00"),
                    recurring = true,
                    announce = true,
                    announcedAt = 1_000,
                )

            RecurringEvents.rollForward(monday, zone)

            assertNull(
                announcedAt(event),
                "next week's meeting is announced afresh at noon on its own day",
            )
        }

    @Test
    fun `an occurrence nobody attended is not banked`() = runBlocking {
        val event =
            seedEvent(
                seedUser(),
                at("2026-07-29T18:00"),
                recurring = true,
                attendance = true,
            )

        RecurringEvents.rollForward(monday, zone)

        assertTrue(occurrences(event).isEmpty())
    }

    // --- Helpers
    // ----------------------------------------------------------------------

    /** Epoch ms of a local "yyyy-MM-ddTHH:mm" moment in the test [zone]. */
    private fun at(local: String): Long =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun localTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().toString()

    private fun localDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun seedUser(): Uuid = transaction {
        Users.insert {
            it[email] = "member${emailCounter.incrementAndGet()}@test.edu"
            it[name] = "Member"
            it[createdAt] = 0
        } get Users.id
    }

    private fun seedEvent(
        author: Uuid,
        startsAt: Long,
        recurring: Boolean,
        attendance: Boolean = false,
        announce: Boolean = false,
        announcedAt: Long? = null,
    ): Long = transaction {
        Events.insert {
            it[title] = "Weekly meeting"
            it[summary] = "A summary"
            it[body] = ""
            it[this.startsAt] = startsAt
            it[this.attendance] = attendance
            it[this.recurring] = recurring
            it[this.announce] = announce
            it[this.announcedAt] = announcedAt
            it[createdBy] = author
            it[createdAt] = 0
        } get Events.id
    }

    private fun seedAttendance(event: Long, user: Uuid) = transaction {
        EventAttendance.insert {
            it[eventID] = event
            it[userID] = user
            it[recordedAt] = 0
        }
    }

    private fun startsAt(event: Long): Long = transaction {
        Events.selectAll().where { Events.id eq event }.single()[Events.startsAt]
    }

    private fun announcedAt(event: Long): Long? = transaction {
        Events.selectAll().where { Events.id eq event }.single()[Events.announcedAt]
    }

    /** The banked (startsAt, headcount) pairs for an event. */
    private fun occurrences(event: Long): List<Pair<Long, Long>> = transaction {
        EventOccurrences.selectAll()
            .where { EventOccurrences.eventID eq event }
            .map { it[EventOccurrences.startsAt] to it[EventOccurrences.attendees] }
    }
}
