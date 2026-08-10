package com.socialcoding.events

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AttendanceTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    @Test
    fun `a check-in is recorded once and reported as already seen after that`() = runBlocking {
        val author = Fixtures.user()
        val event = Fixtures.event(author, attendance = true)
        val member = Fixtures.user()

        assertEquals(AttendOutcome.RECORDED, recordAttendance(event, member))
        assertEquals(AttendOutcome.ALREADY, recordAttendance(event, member))
        assertEquals(1, getAttendeeCount(event), "a repeat scan doesn't inflate the headcount")
    }

    @Test
    fun `checking in to one event doesn't check you in to another`() = runBlocking {
        val author = Fixtures.user()
        val first = Fixtures.event(author, attendance = true)
        val second = Fixtures.event(author, attendance = true)
        val member = Fixtures.user()

        recordAttendance(first, member)

        assertEquals(AttendOutcome.RECORDED, recordAttendance(second, member))
        assertEquals(1, getAttendeeCount(first))
        assertEquals(1, getAttendeeCount(second))
    }

    @Test
    fun `an event nobody attended has a headcount of zero`() = runBlocking {
        assertEquals(0, getAttendeeCount(Fixtures.event(Fixtures.user())))
    }

    @Test
    fun `attendees come back earliest check-in first, with contact details`() = runBlocking {
        val author = Fixtures.user()
        val event = Fixtures.event(author, attendance = true)
        val late = Fixtures.user(name = "Late", email = "late@test.umn.edu")
        val early = Fixtures.user(name = "Early", email = "early@test.umn.edu")
        Fixtures.attend(event, late, at = 2_000)
        Fixtures.attend(event, early, at = 1_000)

        val attendees = getEventAttendees(event)
        assertEquals(listOf("Early", "Late"), attendees.map { it.name })
        // The board exports this list, so it carries the email the check-in was made with.
        assertEquals(listOf("early@test.umn.edu", "late@test.umn.edu"), attendees.map { it.email })
        assertEquals(listOf(1_000L, 2_000L), attendees.map { it.recordedAt })
    }

    @Test
    fun `analytics cover only events with tracking switched on`() = runBlocking {
        val author = Fixtures.user()
        val tracked = Fixtures.event(author, title = "Tracked", attendance = true)
        Fixtures.event(author, title = "Untracked", attendance = false)
        Fixtures.attend(tracked, Fixtures.user())

        val summary = getAttendanceSummary()
        assertEquals(listOf("Tracked"), summary.map { it.title })
        assertEquals(1, summary.single().attendees)
    }

    @Test
    fun `a recurring event reports one entry per meeting, most recent first`() = runBlocking {
        val author = Fixtures.user()
        val event =
            Fixtures.event(author, title = "Weekly", startsAt = 3_000, attendance = true, recurring = true)
        // Two meetings already banked by the nightly roll, plus check-ins for the upcoming one.
        Fixtures.occurrence(event, startsAt = 1_000, attendees = 5)
        Fixtures.occurrence(event, startsAt = 2_000, attendees = 8)
        Fixtures.attend(event, Fixtures.user())

        val summary = getAttendanceSummary()
        assertEquals(listOf(3_000L, 2_000L, 1_000L), summary.map { it.startsAt })
        assertEquals(listOf(1L, 8L, 5L), summary.map { it.attendees })
        assertTrue(summary.all { it.title == "Weekly" && it.eventId == event })
    }

    @Test
    fun `the check-in window opens before the event and closes after it`() {
        // The route gates on these offsets, so their sign and scale are the contract.
        assertTrue(ATTENDANCE_OPENS_MS < 0, "check-in opens before the event starts")
        assertEquals(-60 * 60 * 1000L, ATTENDANCE_OPENS_MS)
        assertEquals(2 * 60 * 60 * 1000L, ATTENDANCE_CLOSES_MS)
    }
}
