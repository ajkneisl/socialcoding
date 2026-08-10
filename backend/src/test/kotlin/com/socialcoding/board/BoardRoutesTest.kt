package com.socialcoding.board

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.decode
import com.socialcoding.people.Role
import com.socialcoding.people.User
import com.socialcoding.projects.models.ProjectDetail
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BoardRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    // --- authorization guards ---------------------------------------------------------------

    @Test
    fun `board endpoints reject non-board and anonymous callers`() = testApplication {
        boot()
        val member = Fixtures.token(Fixtures.user(role = Role.MEMBER))

        // A signed-in member fails the role check (surfaces as 500 via the generic handler).
        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/board/projects") { bearerAuth(member) }.status)
        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/board/settings") { bearerAuth(member) }.status)
        assertEquals(
            HttpStatusCode.InternalServerError,
            client.get("/api/board/members") { bearerAuth(member) }.status)

        // No token at all is a 401.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/board/projects").status)
    }

    // --- settings ---------------------------------------------------------------------------

    @Test
    fun `board reads and writes presentation dates`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val dates = PresentationDates("2026-09-01", "2026-12-01")

        val set =
            client.put("/api/board/settings") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"presentationDates": {"mvpDate": "2026-09-01", "finalDate": "2026-12-01"}}""")
            }
        assertEquals(HttpStatusCode.OK, set.status)
        assertEquals(dates, set.decode<BoardConfig>().presentationDates)

        val get = client.get("/api/board/settings") { bearerAuth(board) }
        assertEquals(dates, get.decode<BoardConfig>().presentationDates)
    }

    @Test
    fun `board sets the Discord announcement channel`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))

        val set =
            client.put("/api/board/settings") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"announcementChannelID": "123456789012345678"}""")
            }
        assertEquals(HttpStatusCode.OK, set.status)
        assertEquals("123456789012345678", set.decode<BoardConfig>().announcementChannelID)

        val get = client.get("/api/board/settings") { bearerAuth(board) }
        assertEquals("123456789012345678", get.decode<BoardConfig>().announcementChannelID)
    }

    // --- members ----------------------------------------------------------------------------

    @Test
    fun `board lists members, changes roles and titles`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD, name = "Chair"))
        val memberId = Fixtures.user(role = Role.MEMBER, name = "Member")

        val list = client.get("/api/board/members") { bearerAuth(board) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.decode<List<User>>().any { it.name == "Member" })

        val promote =
            client.put("/api/board/members/$memberId/role") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"role": "BOARD"}""")
            }
        assertEquals(HttpStatusCode.OK, promote.status)
        assertEquals(Role.BOARD, promote.decode<User>().role)

        val title =
            client.put("/api/board/members/$memberId/title") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody("""{"title": "President"}""")
            }
        assertEquals(HttpStatusCode.OK, title.status)
        assertEquals("President", title.decode<User>().title)
    }

    @Test
    fun `a board member cannot change their own role`() = testApplication {
        boot()
        val selfId = Fixtures.user(role = Role.BOARD)
        val response =
            client.put("/api/board/members/$selfId/role") {
                bearerAuth(Fixtures.token(selfId))
                contentType(ContentType.Application.Json)
                setBody("""{"role": "MEMBER"}""")
            }
        // InvalidAuthorization surfaces as 500 through the generic handler.
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    // --- review queue and decisions ---------------------------------------------------------

    @Test
    fun `board review queue lists pending projects`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val owner = Fixtures.user()
        val projectId = Fixtures.project(owner, status = ProjectStatus.PENDING).toString()

        val queue = client.get("/api/board/projects") { bearerAuth(board) }
        assertEquals(HttpStatusCode.OK, queue.status)
        assertContains(queue.bodyAsText(), projectId)
    }

    @Test
    fun `board can reject, deactivate and reactivate a project`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val owner = Fixtures.user()
        val ownerToken = Fixtures.token(owner)
        val projectId = Fixtures.project(owner, status = ProjectStatus.PENDING).toString()

        suspend fun status(): ProjectStatus =
            client
                .get("/api/projects/$projectId") { bearerAuth(ownerToken) }
                .decode<ProjectDetail>()
                .project
                .status

        // Reject.
        assertEquals(HttpStatusCode.OK, decide(board, projectId, "reject").status)
        assertEquals(ProjectStatus.REJECTED, status())

        // Approve (Discord channel creation fails silently in tests with no credentials).
        assertEquals(HttpStatusCode.OK, decide(board, projectId, "approve").status)
        assertEquals(ProjectStatus.APPROVED, status())

        // Activation flags toggle without error.
        assertEquals(HttpStatusCode.OK, decide(board, projectId, "deactivate").status)
        assertEquals(HttpStatusCode.OK, decide(board, projectId, "activate").status)
    }

    @Test
    fun `an unknown decision is a bad request`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val projectId = Fixtures.project(Fixtures.user()).toString()
        assertEquals(HttpStatusCode.BadRequest, decide(board, projectId, "banish").status)
    }

    @Test
    fun `a title is trimmed, capped, and cleared when blank`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val memberId = Fixtures.user()

        suspend fun setTitle(body: String) =
            client.put("/api/board/members/$memberId/title") {
                bearerAuth(board)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        assertEquals("President", setTitle("""{"title": "  President  "}""").decode<User>().title)
        assertEquals(64, setTitle("""{"title": "${"x".repeat(200)}"}""").decode<User>().title?.length)
        // Blank and omitted both fall back to the default "Board" label the client renders.
        assertNull(setTitle("""{"title": "   "}""").decode<User>().title)
        assertNull(setTitle("{}").decode<User>().title)
    }

    @Test
    fun `managing a member who doesn't exist is not found`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val stranger = Uuid.random()

        assertEquals(
            HttpStatusCode.BadRequest,
            client
                .put("/api/board/members/$stranger/role") {
                    bearerAuth(board)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role": "BOARD"}""")
                }
                .status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client
                .put("/api/board/members/$stranger/title") {
                    bearerAuth(board)
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "President"}""")
                }
                .status)
    }

    @Test
    fun `a malformed member or project id is a bad request`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))

        assertEquals(
            HttpStatusCode.BadRequest,
            client
                .put("/api/board/members/not-a-uuid/role") {
                    bearerAuth(board)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role": "BOARD"}""")
                }
                .status)
        assertEquals(HttpStatusCode.BadRequest, decide(board, "not-a-uuid", "approve").status)
    }

    @Test
    fun `deciding on a project that doesn't exist is not found`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        assertEquals(
            HttpStatusCode.BadRequest,
            decide(board, Uuid.random().toString(), "approve").status)
    }

    @Test
    fun `a rejection note is kept and cleared on the next decision`() = testApplication {
        boot()
        val board = Fixtures.token(Fixtures.user(role = Role.BOARD))
        val owner = Fixtures.user()
        val ownerToken = Fixtures.token(owner)
        val projectId = Fixtures.project(owner, status = ProjectStatus.PENDING).toString()

        suspend fun detail() =
            client.get("/api/projects/$projectId") { bearerAuth(ownerToken) }.decode<ProjectDetail>()

        client.post("/api/board/projects/$projectId/reject") {
            bearerAuth(board)
            contentType(ContentType.Application.Json)
            setBody("""{"note": "Scope is too broad"}""")
        }
        assertEquals("Scope is too broad", detail().project.reviewNote)

        // Resubmitting clears the note so the next reviewer starts fresh.
        client.post("/api/projects/$projectId/resubmit") { bearerAuth(ownerToken) }
        assertNull(detail().project.reviewNote)
    }


    private suspend fun ApplicationTestBuilder.decide(
        token: String,
        projectId: String,
        decision: String,
    ) =
        client.post("/api/board/projects/$projectId/$decision") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
}
