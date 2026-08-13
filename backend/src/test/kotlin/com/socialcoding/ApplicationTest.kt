package com.socialcoding

import com.socialcoding.api.Auth
import com.socialcoding.people.Role
import com.socialcoding.people.Users
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ProjectDetail
import com.socialcoding.projects.models.ProjectStatus
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.*
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ApplicationTest {

  private val json = Json { ignoreUnknownKeys = true }

  /** Inserts a user directly (idempotent across tests sharing the in-memory database). */
  private fun ensureUser(email: String, userName: String, userRole: Role): Uuid = transaction {
    Users.selectAll().where { Users.email eq email }.firstOrNull()?.get(Users.id)
        ?: (Users.insert {
          it[Users.email] = email
          it[name] = userName
          it[role] = userRole
          it[createdAt] = System.currentTimeMillis()
        } get Users.id)
  }

  @Test
  fun `public project listing is reachable`() = testApplication {
    application { rootModule() }
    TestDatabase.connect()
    // the public, optionally-authenticated listing responds without a session
    assertEquals(HttpStatusCode.OK, client.get("/api/projects").status)
  }

  @Test
  fun `design doc lifecycle`() = testApplication {
    application { rootModule() }
    TestDatabase.connect()

    // A project can't be filed until the board has set the dates its milestones inherit.
    Fixtures.presentationDates()

    val creatorId = ensureUser("creator@test.umn.edu", "Creator", Role.MEMBER)
    val teammateId = ensureUser("teammate@test.umn.edu", "Teammate", Role.MEMBER)
    val outsiderId = ensureUser("outsider@test.umn.edu", "Outsider", Role.MEMBER)
    val boardId = ensureUser("board@test.umn.edu", "Board Member", Role.BOARD)

    val creator = Auth.issue(creatorId.toString())
    val teammate = Auth.issue(teammateId.toString())
    val outsider = Auth.issue(outsiderId.toString())
    val board = Auth.issue(boardId.toString())

    suspend fun HttpResponse.detail() = json.decodeFromString<ProjectDetail>(bodyAsText())

    // Create a project through the design doc process. User ids are UUID strings in request bodies.
    val created =
        client.post("/api/projects") {
          bearerAuth(creator)
          contentType(ContentType.Application.Json)
          setBody(
              """
              {
                "title": "Design Doc Test Project",
                "description": "A project created by the test suite",
                "repoUrl": "https://github.com/test/test",
                "teamLeadId": "$creatorId",
                "memberIds": ["$teammateId"],
                "designDoc": {"goal": "Ship a test", "softwareStack": "Ktor, React"},
                "tasks": [
                  {"name": "Build backend", "assigneeIds": ["$creatorId"], "dueDate": "2026-07-01"},
                  {"name": "Build frontend", "dueDate": "2026-08-01", "dependsOn": [0]},
                  {"name": "MVP Presentation", "dueDate": "2026-09-01", "milestone": true},
                  {"name": "Final Presentation", "dueDate": "2026-12-01", "milestone": true}
                ]
              }
              """)
        }
    assertEquals(HttpStatusCode.Created, created.status)
    val detail = created.detail()
    val projectId = detail.project.id
    assertEquals(creatorId.toString(), detail.teamLeadID)
    // The creator is on the team immediately; invited teammates start as pending until they accept.
    assertEquals(setOf(creatorId.toString()), detail.members.map { it.id }.toSet())
    assertEquals(setOf(teammateId.toString()), detail.pendingMembers.map { it.id }.toSet())
    assertEquals(ProjectStatus.PENDING, detail.project.status)
    assertEquals(4, detail.tasks.size)
    // A new project's only design doc is its proposal, filed under the current semester.
    val proposal = detail.designDocs.single()
    assertEquals(DesignDocKind.INITIAL, proposal.kind)
    assertEquals(detail.currentSemester, proposal.semester)
    assertEquals("Ship a test", proposal.initial?.goal)
    // Index-based dependency was translated to the backend task's row id.
    val backendTask = detail.tasks.first { it.name == "Build backend" }
    val frontendTask = detail.tasks.first { it.name == "Build frontend" }
    assertEquals(listOf(backendTask.id), frontendTask.dependsOn)
    assertEquals(listOf(creatorId.toString()), backendTask.assigneeIds)

    // Pending docs are hidden from people outside the team. A hidden project surfaces as NotFound,
    // which the API currently maps onto 400 (every APIError responds BadRequest — arguably this
    // should be 404, but the assertion pins the app's actual behavior).
    assertEquals(
        HttpStatusCode.BadRequest,
        client.get("/api/projects/$projectId") { bearerAuth(outsider) }.status)
    // A pending invitee can't see the project yet either, until they accept.
    assertEquals(
        HttpStatusCode.BadRequest,
        client.get("/api/projects/$projectId") { bearerAuth(teammate) }.status)

    // The invited teammate accepts, joining the team for real.
    assertEquals(
        HttpStatusCode.OK,
        client.post("/api/projects/$projectId/invite/accept") { bearerAuth(teammate) }.status)

    val teammateView = client.get("/api/projects/$projectId") { bearerAuth(teammate) }
    assertEquals(HttpStatusCode.OK, teammateView.status)
    assertTrue(teammateView.detail().canEdit)
    assertFalse(teammateView.detail().canManageTeam)
    assertEquals(
        HttpStatusCode.OK, client.get("/api/projects/$projectId") { bearerAuth(board) }.status)

    // Any teammate can edit the answers.
    val edited =
        client.put("/api/projects/$projectId/design") {
          bearerAuth(teammate)
          contentType(ContentType.Application.Json)
          setBody(
              """
              {
                "title": "Design Doc Test Project",
                "description": "A project created by the test suite",
                "designDoc": {"goal": "An updated goal"}
              }
              """)
        }
    assertEquals(HttpStatusCode.OK, edited.status)
    assertEquals("An updated goal", edited.detail().designDocs.single().initial?.goal)

    // Only the team lead (or board) can modify the team. An authorization failure currently surfaces
    // as 500 (InvalidAuthorization isn't an APIError, so it falls through to the generic handler —
    // arguably this should be 403, but the assertion pins the app's actual behavior).
    val forbidden =
        client.put("/api/projects/$projectId/members") {
          bearerAuth(teammate)
          contentType(ContentType.Application.Json)
          setBody(
              """{"memberIds": ["$creatorId", "$teammateId", "$outsiderId"], "teamLeadId": "$creatorId"}""")
        }
    assertEquals(HttpStatusCode.InternalServerError, forbidden.status)
    val grown =
        client.put("/api/projects/$projectId/members") {
          bearerAuth(creator)
          contentType(ContentType.Application.Json)
          setBody(
              """{"memberIds": ["$creatorId", "$teammateId", "$outsiderId"], "teamLeadId": "$creatorId"}""")
        }
    assertEquals(HttpStatusCode.OK, grown.status)
    // The lead and the already-accepted teammate stay on the team; the newly added outsider is
    // invited (pending) until they accept.
    val grownDetail = grown.detail()
    assertEquals(
        setOf(creatorId.toString(), teammateId.toString()),
        grownDetail.members.map { it.id }.toSet())
    assertEquals(setOf(outsiderId.toString()), grownDetail.pendingMembers.map { it.id }.toSet())

    // A task update saves the list as submitted. Presentation milestones come from filing a
    // design doc, so they aren't re-added here.
    val replaced =
        client.put("/api/projects/$projectId/tasks") {
          bearerAuth(teammate)
          contentType(ContentType.Application.Json)
          setBody("""{"tasks": [{"name": "Only task", "dueDate": "2026-10-01"}]}""")
        }
    assertEquals(HttpStatusCode.OK, replaced.status)
    assertEquals(listOf("Only task"), replaced.detail().tasks.map { it.name })

    // The board approves the design doc; it becomes visible to everyone signed in. The decision
    // endpoint reads an (optional-note) JSON body, so an empty object is sent.
    assertEquals(
        HttpStatusCode.OK,
        client
            .post("/api/board/projects/$projectId/approve") {
              bearerAuth(board)
              contentType(ContentType.Application.Json)
              setBody("{}")
            }
            .status)
    val afterApproval = client.get("/api/projects/$projectId") { bearerAuth(outsider) }
    assertEquals(HttpStatusCode.OK, afterApproval.status)
    assertEquals(ProjectStatus.APPROVED, afterApproval.detail().project.status)
  }
}
