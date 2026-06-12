package com.socialcoding

import com.socialcoding.auth.SessionTokens
import com.socialcoding.db.Users
import com.socialcoding.people.Role
import com.socialcoding.projects.ProjectDetailDto
import com.socialcoding.projects.ProjectStatus
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ServerTest {

  private val json = Json { ignoreUnknownKeys = true }
  private val sessions = SessionTokens("dev-only-secret-change-me")

  /** Inserts a user directly (idempotent across tests sharing the in-memory database). */
  private fun ensureUser(email: String, userName: String, userRole: Role): Long = transaction {
    Users.selectAll().where { Users.email eq email }.firstOrNull()?.get(Users.id)
        ?: (Users.insert {
          it[Users.email] = email
          it[name] = userName
          it[role] = userRole
          it[createdAt] = System.currentTimeMillis()
        } get Users.id)
  }

  @Test
  fun `test root endpoint`() = testApplication {
    application { rootModule() }
    // verify server root returns 200
    assertEquals(HttpStatusCode.OK, client.get("/").status)
  }

  @Test
  fun `design doc lifecycle`() = testApplication {
    application { rootModule() }
    client.get("/") // boots the app, which connects the database

    val creatorId = ensureUser("creator@test.umn.edu", "Creator", Role.MEMBER)
    val teammateId = ensureUser("teammate@test.umn.edu", "Teammate", Role.MEMBER)
    val outsiderId = ensureUser("outsider@test.umn.edu", "Outsider", Role.MEMBER)
    val boardId = ensureUser("board@test.umn.edu", "Board Member", Role.BOARD)

    val creator = sessions.issue(creatorId, Role.MEMBER)
    val teammate = sessions.issue(teammateId, Role.MEMBER)
    val outsider = sessions.issue(outsiderId, Role.MEMBER)
    val board = sessions.issue(boardId, Role.BOARD)

    suspend fun HttpResponse.detail() = json.decodeFromString<ProjectDetailDto>(bodyAsText())

    // Create a project through the design doc process.
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
                "teamLeadId": $creatorId,
                "memberIds": [$teammateId],
                "designDoc": {"goal": "Ship a test", "softwareStack": "Ktor, React"},
                "tasks": [
                  {"name": "Build backend", "assigneeIds": [$creatorId], "dueDate": "2026-07-01"},
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
    assertEquals(creatorId, detail.teamLeadId)
    assertEquals(setOf(creatorId, teammateId), detail.members.map { it.id }.toSet())
    assertEquals(ProjectStatus.PENDING, detail.project.status)
    assertEquals(4, detail.tasks.size)
    assertEquals("Ship a test", detail.designDoc.goal)
    // Index-based dependency was translated to the backend task's row id.
    val backendTask = detail.tasks.first { it.name == "Build backend" }
    val frontendTask = detail.tasks.first { it.name == "Build frontend" }
    assertEquals(listOf(backendTask.id), frontendTask.dependsOn)
    assertEquals(listOf(creatorId), backendTask.assigneeIds)

    // Pending docs are hidden from people outside the team, visible to the team and board.
    assertEquals(
        HttpStatusCode.NotFound,
        client.get("/api/projects/$projectId") { bearerAuth(outsider) }.status)
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
    assertEquals("An updated goal", edited.detail().designDoc.goal)

    // Only the team lead (or board) can modify the team.
    val forbidden =
        client.put("/api/projects/$projectId/members") {
          bearerAuth(teammate)
          contentType(ContentType.Application.Json)
          setBody(
              """{"memberIds": [$creatorId, $teammateId, $outsiderId], "teamLeadId": $creatorId}""")
        }
    assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    val grown =
        client.put("/api/projects/$projectId/members") {
          bearerAuth(creator)
          contentType(ContentType.Application.Json)
          setBody(
              """{"memberIds": [$creatorId, $teammateId, $outsiderId], "teamLeadId": $creatorId}""")
        }
    assertEquals(HttpStatusCode.OK, grown.status)
    assertEquals(
        setOf(creatorId, teammateId, outsiderId), grown.detail().members.map { it.id }.toSet())

    // Required milestones are restored even if a task update drops them.
    val replaced =
        client.put("/api/projects/$projectId/tasks") {
          bearerAuth(teammate)
          contentType(ContentType.Application.Json)
          setBody("""{"tasks": [{"name": "Only task", "dueDate": "2026-10-01"}]}""")
        }
    assertEquals(HttpStatusCode.OK, replaced.status)
    val replacedNames = replaced.detail().tasks.map { it.name }
    assertContains(replacedNames, "Only task")
    assertContains(replacedNames, "MVP Presentation")
    assertContains(replacedNames, "Final Presentation")

    // The board approves the design doc; it becomes visible to everyone signed in.
    assertEquals(
        HttpStatusCode.OK,
        client.post("/api/board/projects/$projectId/approve") { bearerAuth(board) }.status)
    val afterApproval = client.get("/api/projects/$projectId") { bearerAuth(outsider) }
    assertEquals(HttpStatusCode.OK, afterApproval.status)
    assertEquals(ProjectStatus.APPROVED, afterApproval.detail().project.status)
  }
}
