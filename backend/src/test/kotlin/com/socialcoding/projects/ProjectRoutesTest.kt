package com.socialcoding.projects

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.board.PresentationDates
import com.socialcoding.decode
import com.socialcoding.projects.likes.models.LikeResult
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.models.Project
import com.socialcoding.projects.models.ProjectDetail
import com.socialcoding.projects.models.ProjectShowcase
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProjectRoutesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    @Test
    fun `public listing returns approved projects`() = testApplication {
        boot()
        val owner = Fixtures.user()
        Fixtures.project(owner, status = ProjectStatus.APPROVED, title = "Shipped")
        Fixtures.project(owner, status = ProjectStatus.PENDING, title = "Hidden")

        val projects = client.get("/api/projects").decode<List<Project>>()
        assertTrue(projects.any { it.title == "Shipped" })
        assertFalse(projects.any { it.title == "Hidden" }, "pending projects aren't public")
    }

    @Test
    fun `showcase is public for approved projects and hidden otherwise`() = testApplication {
        boot()
        val owner = Fixtures.user(name = "Builder")
        val approved = Fixtures.project(owner, status = ProjectStatus.APPROVED, title = "Live")
        val pending = Fixtures.project(owner, status = ProjectStatus.PENDING)

        val showcase = client.get("/api/projects/$approved/showcase")
        assertEquals(HttpStatusCode.OK, showcase.status)
        assertEquals("Live", showcase.decode<ProjectShowcase>().project.title)

        // A non-approved project isn't publicly visible (NotFound → 400).
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/api/projects/$pending/showcase").status)
    }

    @Test
    fun `mine and presentation-dates require a session`() = testApplication {
        boot()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/projects/mine").status)
        assertEquals(
            HttpStatusCode.Unauthorized, client.get("/api/projects/presentation-dates").status)
    }

    @Test
    fun `mine returns the caller's own projects`() = testApplication {
        boot()
        val owner = Fixtures.user()
        Fixtures.project(owner, title = "My Thing")
        val otherOwner = Fixtures.user()
        Fixtures.project(otherOwner, title = "Someone Else's")

        val mine = client.get("/api/projects/mine") { bearerAuth(Fixtures.token(owner)) }
        assertEquals(HttpStatusCode.OK, mine.status)
        val titles = mine.decode<List<Project>>().map { it.title }
        assertTrue(titles.contains("My Thing"))
        assertFalse(titles.contains("Someone Else's"))
    }

    @Test
    fun `presentation dates are readable by any signed-in user`() = testApplication {
        boot()
        val token = Fixtures.token(Fixtures.user())
        val response = client.get("/api/projects/presentation-dates") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        // Defaults are blank until the board sets them.
        assertEquals(PresentationDates("", ""), response.decode<PresentationDates>())
    }

    @Test
    fun `invites lists pending invites and accepting joins the team`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val invitee = Fixtures.user()
        val inviteeToken = Fixtures.token(invitee)
        val projectId = Fixtures.project(owner)
        Fixtures.invite(projectId, invitee, MemberStatus.PENDING)

        val invites = client.get("/api/projects/invites") { bearerAuth(inviteeToken) }
        assertEquals(HttpStatusCode.OK, invites.status)
        assertTrue(invites.decode<List<Project>>().any { it.id == projectId.toString() })

        // Accepting the invite moves the user onto the team.
        assertEquals(
            HttpStatusCode.OK,
            client
                .post("/api/projects/$projectId/invite/accept") { bearerAuth(inviteeToken) }
                .status)
        assertTrue(client.get("/api/projects/invites") { bearerAuth(inviteeToken) }
            .decode<List<Project>>()
            .isEmpty())
        assertTrue(client.get("/api/projects/mine") { bearerAuth(inviteeToken) }
            .decode<List<Project>>()
            .any { it.id == projectId.toString() })
    }

    @Test
    fun `declining an invite drops it, and declining twice is not found`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val invitee = Fixtures.user()
        val inviteeToken = Fixtures.token(invitee)
        val projectId = Fixtures.project(owner)
        Fixtures.invite(projectId, invitee, MemberStatus.PENDING)

        assertEquals(
            HttpStatusCode.OK,
            client
                .post("/api/projects/$projectId/invite/decline") { bearerAuth(inviteeToken) }
                .status)
        // The invite is gone, so a second decline is NotFound (→ 400).
        assertEquals(
            HttpStatusCode.BadRequest,
            client
                .post("/api/projects/$projectId/invite/decline") { bearerAuth(inviteeToken) }
                .status)
    }

    @Test
    fun `only rejected projects can be resubmitted, and only by a manager`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val ownerToken = Fixtures.token(owner)
        val rejected = Fixtures.project(owner, status = ProjectStatus.REJECTED)

        // An outsider can't even see the rejected project, so it reads as NotFound (→ 400) before
        // the manage-team check is reached.
        val outsider = Fixtures.token(Fixtures.user())
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/api/projects/$rejected/resubmit") { bearerAuth(outsider) }.status)

        // The owner (team lead) resubmits; the project returns to pending review.
        val resubmit = client.post("/api/projects/$rejected/resubmit") { bearerAuth(ownerToken) }
        assertEquals(HttpStatusCode.OK, resubmit.status)
        assertEquals(ProjectStatus.PENDING, resubmit.decode<ProjectDetail>().project.status)

        // A pending project can't be resubmitted again.
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/api/projects/$rejected/resubmit") { bearerAuth(ownerToken) }.status)
    }

    @Test
    fun `liking an approved project toggles the heart`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val fan = Fixtures.token(Fixtures.user())
        val approved = Fixtures.project(owner, status = ProjectStatus.APPROVED)

        val liked = client.post("/api/projects/$approved/like") { bearerAuth(fan) }
        assertEquals(HttpStatusCode.OK, liked.status)
        assertEquals(LikeResult(liked = true, likes = 1), liked.decode<LikeResult>())

        val unliked = client.post("/api/projects/$approved/like") { bearerAuth(fan) }
        assertEquals(LikeResult(liked = false, likes = 0), unliked.decode<LikeResult>())
    }

    @Test
    fun `liking a non-approved project is not found`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val pending = Fixtures.project(owner, status = ProjectStatus.PENDING)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/api/projects/$pending/like") { bearerAuth(Fixtures.token(owner)) }.status)
    }

    @Test
    fun `a malformed project id is not found rather than a server error`() = testApplication {
        boot()
        val token = Fixtures.token(Fixtures.user())
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/api/projects/not-a-uuid") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/projects/not-a-uuid/showcase").status)
    }

    // --- Creating -----------------------------------------------------------------------------

    @Test
    fun `creating a project requires a title and a description`() = testApplication {
        boot()
        val token = Fixtures.token(Fixtures.user())

        listOf(
                """{"title": "   ", "description": "Something"}""",
                """{"title": "Something", "description": ""}""",
            )
            .forEach { body ->
                val response =
                    client.post("/api/projects") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                assertEquals(HttpStatusCode.BadRequest, response.status, body)
            }
    }

    @Test
    fun `a team lead who isn't a real user falls back to the creator`() = testApplication {
        boot()
        val creator = Fixtures.user()

        val created =
            client.post("/api/projects") {
                bearerAuth(Fixtures.token(creator))
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "title": "Solo project",
                      "description": "Just me",
                      "teamLeadId": "${Uuid.random()}",
                      "memberIds": ["${Uuid.random()}"]
                    }
                    """)
            }

        assertEquals(HttpStatusCode.Created, created.status)
        val detail = created.decode<ProjectDetail>()
        assertEquals(creator.toString(), detail.teamLeadID)
        // Ids that match no user are dropped rather than invited.
        assertTrue(detail.pendingMembers.isEmpty())
        assertEquals(listOf(creator.toString()), detail.members.map { it.id })
    }

    @Test
    fun `a new project always carries the presentation milestones`() = testApplication {
        boot()
        val creator = Fixtures.user()

        val detail =
            client
                .post("/api/projects") {
                    bearerAuth(Fixtures.token(creator))
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "Milestones", "description": "d", "tasks": []}""")
                }
                .decode<ProjectDetail>()

        assertEquals(
            listOf("MVP Presentation", "Final Presentation"),
            detail.tasks.map { it.name },
        )
        assertTrue(detail.tasks.all { it.milestone })
    }

    // --- Editing ------------------------------------------------------------------------------

    @Test
    fun `editing the design doc requires a title and description`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        val response =
            client.put("/api/projects/$project/design") {
                bearerAuth(Fixtures.token(owner))
                contentType(ContentType.Application.Json)
                setBody("""{"title": " ", "description": "d", "designDoc": {}}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `someone outside the team cannot see or edit the design doc`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)
        val outsider = Fixtures.token(Fixtures.user())

        // A doc they can't see reads as NotFound before any permission check (→ 400).
        val response =
            client.put("/api/projects/$project/design") {
                bearerAuth(outsider)
                contentType(ContentType.Application.Json)
                setBody("""{"title": "Hijacked", "description": "d", "designDoc": {}}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- Team management ----------------------------------------------------------------------

    @Test
    fun `a team lead who isn't a valid user is rejected`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        listOf("not-a-uuid", Uuid.random().toString()).forEach { leadId ->
            val response =
                client.put("/api/projects/$project/members") {
                    bearerAuth(Fixtures.token(owner))
                    contentType(ContentType.Application.Json)
                    setBody("""{"memberIds": [], "teamLeadId": "$leadId"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status, leadId)
        }
    }

    @Test
    fun `dropping a member strips them from task assignments`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val leaving = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.member(project, owner)
        Fixtures.member(project, leaving)
        Fixtures.task(project, "Shared work", dueDate = "2026-05-01", assignees = listOf(owner, leaving))

        val updated =
            client.put("/api/projects/$project/members") {
                bearerAuth(Fixtures.token(owner))
                contentType(ContentType.Application.Json)
                setBody("""{"memberIds": ["$owner"], "teamLeadId": "$owner"}""")
            }

        assertEquals(HttpStatusCode.OK, updated.status)
        val detail = updated.decode<ProjectDetail>()
        assertEquals(listOf(owner.toString()), detail.members.map { it.id })
        val task = detail.tasks.single { it.name == "Shared work" }
        assertEquals(listOf(owner.toString()), task.assigneeIds, "the departed member is unassigned")
    }

    @Test
    fun `an existing member keeps their accepted status through a team edit`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val settled = Fixtures.user()
        val invited = Fixtures.user()
        val newcomer = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.member(project, settled)
        Fixtures.invite(project, invited, MemberStatus.PENDING)

        val updated =
            client.put("/api/projects/$project/members") {
                bearerAuth(Fixtures.token(owner))
                contentType(ContentType.Application.Json)
                setBody(
                    """{"memberIds": ["$settled", "$invited", "$newcomer"], "teamLeadId": "$owner"}""")
            }

        val detail = updated.decode<ProjectDetail>()
        assertEquals(setOf(owner.toString(), settled.toString()), detail.members.map { it.id }.toSet())
        // The outstanding invite isn't re-sent, and the newcomer gets one of their own.
        assertEquals(
            setOf(invited.toString(), newcomer.toString()),
            detail.pendingMembers.map { it.id }.toSet(),
        )
    }
}
