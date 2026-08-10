package com.socialcoding.events

import com.socialcoding.TestDatabase
import com.socialcoding.board.BoardConfig
import com.socialcoding.board.BoardSettings
import com.socialcoding.people.Users
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private class FakeAnnouncer(private val succeed: Boolean = true) :
    EventAnnouncements.Announcer {
    val posts = mutableListOf<Pair<String, String>>()

    override suspend fun send(channelID: String, content: String): Boolean {
        posts.add(channelID to content)
        return succeed
    }
}

class AnnouncementsTest {
    private val channel = "987654321098765432"

    /** A fixed zone keeps "noon on the day" stable wherever the suite runs. */
    private val zone = ZoneId.of("America/Chicago")

    private val emailCounter = AtomicInteger()

    @BeforeTest fun clean() = TestDatabase.reset()

    // --- Message rendering
    // -------------------------------------------------------------

    @Test
    fun `an announcement carries the details a member needs`() {
        val message =
            EventAnnouncements.buildMessage(
                event(
                    id = 42,
                    title = "Kickoff Meeting",
                    summary = "Come meet the teams.",
                    startsAt = 1_786_000_000_000,
                    location = "Bruininks 315",
                )
            )

        assertContains(message, "**Kickoff Meeting**")
        assertContains(message, "Come meet the teams.")
        assertContains(message, "Bruininks 315")
        // A Discord timestamp renders in each member's own timezone.
        assertContains(message, "<t:1786000000:F>")
        assertContains(message, "/events/42")
    }

    @Test
    fun `a weekly event says so, a one-off doesn't`() {
        assertContains(
            EventAnnouncements.buildMessage(event(recurring = true)),
            "repeats weekly",
        )
        assertFalse(
            EventAnnouncements.buildMessage(event(recurring = false))
                .contains("repeats weekly")
        )
    }

    @Test
    fun `a location-less event doesn't leave a dangling separator`() {
        val details =
            EventAnnouncements.buildMessage(event(location = null)).lines()[1]

        assertFalse(details.endsWith("·"))
        assertFalse(details.contains("· ·"))
    }

    // --- Announcing
    // --------------------------------------------------------------------

    @Test
    fun `the noon sweep posts the day's events and stamps them`(): Unit = runBlocking {
        withChannel()
        val stored = seedEvent(at("2026-08-05T18:00"))

        val announcer = FakeAnnouncer()
        assertEquals(1, sweep(noonOn("2026-08-05"), announcer))

        assertEquals(channel, announcer.posts.single().first)
        assertContains(announcer.posts.single().second, "Weekly meeting")
        assertNotNull(
            getEventByID(stored.id)?.announcedAt,
            "the post is recorded on the event",
        )
    }

    @Test
    fun `an event isn't announced before noon on its day`() = runBlocking {
        withChannel()
        val stored = seedEvent(at("2026-08-05T18:00"))

        val announcer = FakeAnnouncer()
        assertEquals(
            0,
            sweep(LocalDateTime.parse("2026-08-05T11:59"), announcer),
            "11:59 is too early",
        )
        assertTrue(announcer.posts.isEmpty())
        assertNull(getEventByID(stored.id)?.announcedAt)
    }

    @Test
    fun `only events happening today are announced`() = runBlocking {
        withChannel()
        seedEvent(at("2026-08-12T18:00")) // next week
        seedEvent(at("2026-07-29T18:00")) // last week, window missed for good

        val announcer = FakeAnnouncer()
        assertEquals(0, sweep(noonOn("2026-08-05"), announcer))
        assertTrue(announcer.posts.isEmpty())
    }

    @Test
    fun `events that didn't ask to be announced are left alone`() = runBlocking {
        withChannel()
        val quiet = seedEvent(at("2026-08-05T18:00"), announce = false)

        val announcer = FakeAnnouncer()
        assertEquals(0, sweep(noonOn("2026-08-05"), announcer))
        assertTrue(announcer.posts.isEmpty())
        assertNull(getEventByID(quiet.id)?.announcedAt)
    }

    @Test
    fun `nothing is posted when the board hasn't set a channel`() = runBlocking {
        val stored = seedEvent(at("2026-08-05T18:00"))

        val announcer = FakeAnnouncer()
        assertEquals(0, sweep(noonOn("2026-08-05"), announcer))

        assertTrue(announcer.posts.isEmpty())
        assertNull(getEventByID(stored.id)?.announcedAt)
    }

    @Test
    fun `a second sweep the same day posts nothing new`() = runBlocking {
        withChannel()
        seedEvent(at("2026-08-05T18:00"))

        assertEquals(1, sweep(noonOn("2026-08-05"), FakeAnnouncer()))

        val second = FakeAnnouncer()
        assertEquals(0, sweep(noonOn("2026-08-05"), second))
        assertTrue(second.posts.isEmpty())
    }

    @Test
    fun `a failed post isn't stamped, so a later sweep retries`() = runBlocking {
        withChannel()
        val stored = seedEvent(at("2026-08-05T18:00"))

        assertEquals(
            0,
            sweep(noonOn("2026-08-05"), FakeAnnouncer(succeed = false)),
        )
        assertNull(
            getEventByID(stored.id)?.announcedAt,
            "a failed post leaves the event unannounced",
        )

        val retry = FakeAnnouncer(succeed = true)
        assertEquals(1, sweep(noonOn("2026-08-05"), retry))
        assertEquals(1, retry.posts.size)
    }

    // --- Publishing late in the day
    // ----------------------------------------------------

    @Test
    fun `publishing after noon on the day announces right away`(): Unit = runBlocking {
        withChannel()
        val stored = seedEvent(at("2026-08-05T19:00"))

        val announcer = FakeAnnouncer()
        assertTrue(
            EventAnnouncements.announceIfDue(
                stored,
                LocalDateTime.parse("2026-08-05T15:30"),
                zone,
                announcer,
            ),
            "the sweep already ran today, so publishing delivers it",
        )
        assertEquals(1, announcer.posts.size)
        assertNotNull(getEventByID(stored.id)?.announcedAt)
    }

    @Test
    fun `publishing ahead of time leaves delivery to the sweep`() = runBlocking {
        withChannel()
        val stored = seedEvent(at("2026-08-12T18:00"))

        val announcer = FakeAnnouncer()
        assertFalse(
            EventAnnouncements.announceIfDue(
                stored,
                LocalDateTime.parse("2026-08-05T15:30"),
                zone,
                announcer,
            )
        )
        assertTrue(announcer.posts.isEmpty())

        // It goes out at noon on the day itself.
        assertEquals(1, sweep(noonOn("2026-08-12"), FakeAnnouncer()))
    }

    // --- Channel setting
    // ---------------------------------------------------------------

    @Test
    fun `the channel setting keeps only digits and survives a date save`() {
        BoardSettings.setConfig(
            BoardConfig(announcementChannelID = "  <#$channel>  ")
        )
        assertEquals(channel, BoardSettings.announcementChannelID())

        // Saving dates through the full config must not wipe the channel.
        val existing = BoardSettings.config()
        BoardSettings.setConfig(
            existing.copy(
                presentationDates =
                    existing.presentationDates.copy(mvpDate = "2026-10-01")
            )
        )

        val after = BoardSettings.config()
        assertEquals(channel, after.announcementChannelID)
        assertEquals("2026-10-01", after.presentationDates.mvpDate)
    }

    @Test
    fun `clearing the channel turns announcing off`() {
        BoardSettings.setConfig(BoardConfig(announcementChannelID = channel))
        BoardSettings.setConfig(BoardConfig(announcementChannelID = ""))

        assertEquals("", BoardSettings.announcementChannelID())
    }

    // --- Helpers
    // ----------------------------------------------------------------------

    /** An unsaved [Event] for the rendering tests, which never touch the database. */
    private fun event(
        id: Long = 1,
        title: String = "Weekly meeting",
        summary: String = "A summary",
        startsAt: Long = 1_786_000_000_000,
        location: String? = null,
        recurring: Boolean = false,
        announcedAt: Long? = null,
    ) =
        Event(
            id = id,
            title = title,
            summary = summary,
            body = "",
            startsAt = startsAt,
            location = location,
            burrowUrl = null,
            imageUrl = null,
            attendance = false,
            recurring = recurring,
            announce = true,
            announcedAt = announcedAt,
            authorName = "Author",
            createdAt = 0,
        )

    /** Points announcements at [channel]. */
    private fun withChannel() =
        BoardSettings.setConfig(BoardConfig(announcementChannelID = channel))

    /** Runs a sweep in the fixed test [zone]. */
    private suspend fun sweep(now: LocalDateTime, announcer: FakeAnnouncer) =
        EventAnnouncements.sweep(now, zone, announcer)

    /** Epoch ms of a local "yyyy-MM-ddTHH:mm" moment in the test [zone]. */
    private fun at(local: String): Long =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    /** Noon on a "yyyy-MM-dd" day — when the sweep fires. */
    private fun noonOn(day: String): LocalDateTime =
        LocalDate.parse(day).atTime(12, 0)

    /** Inserts an event and reads it back, so the returned [Event] matches what routes hand around. */
    private fun seedEvent(
        startsAt: Long = 1_786_000_000_000,
        announce: Boolean = true,
    ): Event {
        val id = transaction {
            val author =
                Users.insert {
                    it[email] = "author${emailCounter.incrementAndGet()}@test.edu"
                    it[name] = "Author"
                    it[createdAt] = 0
                } get Users.id

            Events.insert {
                it[title] = "Weekly meeting"
                it[summary] = "A summary"
                it[body] = ""
                it[this.startsAt] = startsAt
                it[this.announce] = announce
                it[createdBy] = author
                it[createdAt] = 0
            } get Events.id
        }

        return getEventByID(id)!!
    }
}
