package com.socialcoding.events

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.decode
import com.socialcoding.people.Role
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    /** Creates an event as the board and returns it. */
    private suspend fun ApplicationTestBuilder.createEvent(
        board: String,
        title: String = "Kickoff",
        startsAt: Long = System.currentTimeMillis(),
        attendance: Boolean = false,
    ): Event {
        val response =
            client.post("/api/events") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"title": "$title", "summary": "A summary", "startsAt": $startsAt, "attendance": $attendance}""")
            }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.decode<Event>()
    }

    @Test
    fun `event listing is public`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        createEvent(board, title = "Public Event")

        val response = client.get("/api/events")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.decode<List<Event>>().any { it.title == "Public Event" })
    }

    @Test
    fun `only board members create events`() = testApplication {
        boot()
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))

        // Anonymous.
        assertEquals(
            HttpStatusCode.Unauthorized,
            client
                .post("/api/events") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "x", "summary": "y", "startsAt": 0}""")
                }
                .status)

        // Signed-in member (role check → 500).
        assertEquals(
            HttpStatusCode.InternalServerError,
            client
                .post("/api/events") {
                    bearerAuth(member)
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "x", "summary": "y", "startsAt": 0}""")
                }
                .status)
    }

    @Test
    fun `creating an event requires a title and summary`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val response =
            client.post("/api/events") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"title": "  ", "summary": "", "startsAt": 0}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `board updates and deletes events`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val event = createEvent(board, title = "Original")

        val updated =
            client.put("/api/events/${event.id}") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"title": "Renamed", "summary": "New summary", "startsAt": 123}""")
            }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("Renamed", updated.decode<Event>().title)

        assertEquals(
            HttpStatusCode.OK,
            client.delete("/api/events/${event.id}") { bearerAuth(board) }.status)
        // Gone from the public listing.
        assertFalse(client.get("/api/events").decode<List<Event>>().any { it.id == event.id })
    }

    @Test
    fun `announce is stored for the noon sweep rather than posted on publish`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))

        // A week out, so the event's noon hasn't arrived: the flag is recorded and delivery waits.
        val startsAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
        val response =
            client.post("/api/events") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"title": "Big news", "summary": "s", "startsAt": $startsAt, "announce": true}""")
            }
        assertEquals(HttpStatusCode.Created, response.status)

        val event = response.decode<Event>()
        assertTrue(event.announce, "the request to announce is remembered")
        assertNull(event.announcedAt, "nothing has gone out yet")

        // Unticking it cancels the queued announcement.
        val quieted =
            client
                .put("/api/events/${event.id}") {
                    bearerAuth(board)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"title": "Big news", "summary": "s", "startsAt": $startsAt, "announce": false}""")
                }
                .decode<Event>()
        assertFalse(quieted.announce)
    }

    @Test
    fun `updating a missing event is not found`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val response =
            client.put("/api/events/999999") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"title": "x", "summary": "y", "startsAt": 0}""")
            }
        // NotFound maps to 400.
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `attendance check-in is idempotent within the window`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))
        val event = createEvent(board, attendance = true)

        val first = client.post("/api/events/${event.id}/attend") { bearerAuth(member) }
        assertEquals(HttpStatusCode.OK, first.status)
        assertContains(first.bodyAsText(), "RECORDED")

        val second = client.post("/api/events/${event.id}/attend") { bearerAuth(member) }
        assertEquals(HttpStatusCode.OK, second.status)
        assertContains(second.bodyAsText(), "ALREADY")
    }

    @Test
    fun `attendance is rejected when disabled or outside the window`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))

        val noAttendance = createEvent(board, attendance = false)
        val disabled = client.post("/api/events/${noAttendance.id}/attend") { bearerAuth(member) }
        assertEquals(HttpStatusCode.BadRequest, disabled.status)
        // The check-in page shows this reason verbatim, so the body has to carry it
        // under the same key an APIError serializes to.
        assertEquals(
            """{"error":"Attendance isn't enabled for this event."}""",
            disabled.bodyAsText(),
        )

        // Starts tomorrow: check-in hasn't opened yet.
        val future =
            createEvent(
                board,
                attendance = true,
                startsAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
            )
        val tooEarly = client.post("/api/events/${future.id}/attend") { bearerAuth(member) }
        assertEquals(HttpStatusCode.BadRequest, tooEarly.status)
        assertEquals(
            """{"error":"Check-in opens an hour before the event starts."}""",
            tooEarly.bodyAsText(),
        )
    }

    @Test
    fun `checking in to an event that isn't there says so`() = testApplication {
        boot()
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))

        // NotFound is thrown, not responded, so this covers the StatusPages path.
        val missing = client.post("/api/events/999999/attend") { bearerAuth(member) }
        assertEquals(
            """{"error":"That event could not be found."}""",
            missing.bodyAsText(),
        )
    }

    @Test
    fun `board reads attendee lists and analytics`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val memberId = Fixtures.user(role = Role.MEMBER, name = "Attendee")
        val event = createEvent(board, attendance = true)
        client.post("/api/events/${event.id}/attend") { bearerAuth(Fixtures.token(memberId)) }

        val attendees = client.get("/api/events/${event.id}/attendance") { bearerAuth(board) }
        assertEquals(HttpStatusCode.OK, attendees.status)
        assertTrue(attendees.decode<List<Attendee>>().any { it.name == "Attendee" })

        val analytics = client.get("/api/events/analytics") { bearerAuth(board) }
        assertEquals(HttpStatusCode.OK, analytics.status)
        assertContains(analytics.bodyAsText(), event.title)
    }

    @Test
    fun `attendee export and analytics are board-only`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))
        val event = createEvent(board, attendance = true)

        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/events/${event.id}/attendance") { bearerAuth(member) }.status)
        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/events/analytics") { bearerAuth(member) }.status)
    }
}
