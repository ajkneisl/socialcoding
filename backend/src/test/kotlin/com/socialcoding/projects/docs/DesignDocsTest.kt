package com.socialcoding.projects.docs

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.board.BoardSettings
import com.socialcoding.board.PresentationDates
import com.socialcoding.board.semesterLabel
import com.socialcoding.decode
import com.socialcoding.projects.Projects
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ProjectDetail
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.stampPresentationMilestones
import com.socialcoding.rootModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class DesignDocsTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    private fun ApplicationTestBuilder.boot() {
        application { rootModule() }
        TestDatabase.connect()
    }

    private val utc = ZoneId.of("UTC")

    private fun millisAt(year: Int, month: Int) =
        ZonedDateTime.of(year, month, 15, 12, 0, 0, 0, utc).toInstant().toEpochMilli()

    /** Files [semester]'s proposal for [project] so it looks like a project already underway. */
    private fun proposal(project: Uuid, semester: String, goal: String = "Ship it") = transaction {
        insertDesignDoc(
            projectID = project,
            semester = semester,
            kind = DesignDocKind.INITIAL,
            content = encodeDesignDoc(DesignDocContent(goal = goal)),
        )
    }

    @Test
    fun `semester labels follow the university calendar`() {
        assertEquals("Spring 2026", semesterLabel(millisAt(2026, 1), utc))
        assertEquals("Spring 2026", semesterLabel(millisAt(2026, 5), utc))
        assertEquals("Summer 2026", semesterLabel(millisAt(2026, 6), utc))
        assertEquals("Summer 2026", semesterLabel(millisAt(2026, 7), utc))
        assertEquals("Fall 2026", semesterLabel(millisAt(2026, 8), utc))
        assertEquals("Fall 2026", semesterLabel(millisAt(2026, 12), utc))
    }

    @Test
    fun `the current semester follows the calendar until the board pins one`() {
        TestDatabase.connect()
        assertEquals(semesterLabel(System.currentTimeMillis()), BoardSettings.currentSemester())

        BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2030")
        assertEquals("Spring 2030", BoardSettings.currentSemester())
    }

    @Test
    fun `saving the derived label leaves the semester on the calendar`() {
        TestDatabase.connect()
        val derived = semesterLabel(System.currentTimeMillis())

        // The settings form hands back whatever config() showed it, which is the derived label when
        // nothing is pinned — that must not pin it.
        BoardSettings.setConfig(BoardSettings.config().copy(currentSemester = derived))
        assertEquals("", BoardSettings.get(BoardSettings.CURRENT_SEMESTER))

        BoardSettings.setConfig(BoardSettings.config().copy(currentSemester = "Fall 2031"))
        assertEquals("Fall 2031", BoardSettings.get(BoardSettings.CURRENT_SEMESTER))
    }

    @Test
    fun `a returning doc is filed for the new semester and goes back to the board`() =
        testApplication {
            boot()
            val owner = Fixtures.user()
            val token = Fixtures.token(owner)
            val project = Fixtures.project(owner, status = ProjectStatus.APPROVED)
            proposal(project, "Fall 2026")
            transaction {
                Projects.update({ Projects.id eq project }) { it[reviewNote] = "Looks good" }
            }
            BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2027")

            val filed =
                client.put("/api/projects/$project/semester-doc") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"designDoc": {"accomplishments": "Shipped the MVP", "goals": "Launch"}}
                        """)
                }
            assertEquals(HttpStatusCode.OK, filed.status)

            val detail = filed.decode<ProjectDetail>()
            assertEquals("Spring 2027", detail.currentSemester)
            assertEquals(2, detail.designDocs.size)
            // Newest first, so this semester's check-in leads.
            val current = detail.designDocs.first()
            assertEquals(DesignDocKind.RETURNING, current.kind)
            assertEquals("Spring 2027", current.semester)
            assertEquals("Shipped the MVP", current.returning?.accomplishments)
            assertNull(current.initial)
            // The proposal is still there, untouched.
            assertEquals("Ship it", detail.designDocs.last().initial?.goal)

            // Coming back for another semester means another review.
            assertEquals(ProjectStatus.PENDING, detail.project.status)
            assertNull(detail.project.reviewNote)
        }

    @Test
    fun `filing a returning doc replaces the presentation milestones with the new dates`() =
        testApplication {
            boot()
            val owner = Fixtures.user()
            val token = Fixtures.token(owner)
            val project = Fixtures.project(owner, status = ProjectStatus.APPROVED)
            proposal(project, "Fall 2026")
            // Last semester's milestones, plus a deliverable the team wrote themselves.
            transaction {
                stampPresentationMilestones(
                    project,
                    PresentationDates(mvpDate = "2026-09-01", finalDate = "2026-12-01"),
                )
            }
            Fixtures.task(project, "Build it", dueDate = "2026-05-01")

            BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2027")
            BoardSettings.set(BoardSettings.MVP_DATE, "2027-02-15")
            BoardSettings.set(BoardSettings.FINAL_DATE, "2027-05-01")

            val tasks =
                client
                    .put("/api/projects/$project/semester-doc") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody("""{"designDoc": {"goals": "Another semester"}}""")
                    }
                    .decode<ProjectDetail>()
                    .tasks

            // Ordered by due date, so the team's deliverable leads and each milestone appears once.
            assertEquals(
                listOf("Build it", "MVP Presentation", "Final Presentation"),
                tasks.map { it.name },
                "the old pair is replaced, not duplicated",
            )
            val byName = tasks.associate { it.name to it.dueDate }
            assertEquals("2027-02-15", byName["MVP Presentation"])
            assertEquals("2027-05-01", byName["Final Presentation"])
            assertEquals("2026-05-01", byName["Build it"], "the team's own deliverable is untouched")
        }

    @Test
    fun `editing this semester's doc again leaves the review alone`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val token = Fixtures.token(owner)
        val project = Fixtures.project(owner, status = ProjectStatus.APPROVED)
        proposal(project, "Fall 2026")
        BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2027")

        suspend fun file(goals: String) =
            client.put("/api/projects/$project/semester-doc") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"designDoc": {"goals": "$goals"}}""")
            }

        assertEquals(HttpStatusCode.OK, file("First pass").status)
        // The board gets to it before the team stops editing.
        transaction {
            Projects.update({ Projects.id eq project }) { it[status] = ProjectStatus.APPROVED }
        }

        val edited = file("Second pass").decode<ProjectDetail>()
        assertEquals(2, edited.designDocs.size, "editing replaces the answers, it doesn't refile")
        assertEquals("Second pass", edited.designDocs.first().returning?.goals)
        assertEquals(ProjectStatus.APPROVED, edited.project.status)
    }

    @Test
    fun `a project can't file a check-in for the semester it started in`() = testApplication {
        boot()
        val owner = Fixtures.user()
        val token = Fixtures.token(owner)
        val project = Fixtures.project(owner)
        BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Fall 2026")
        proposal(project, "Fall 2026")

        val response =
            client.put("/api/projects/$project/semester-doc") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody("""{"designDoc": {"goals": "Too soon"}}""")
            }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a past proposal's answers are read only, but the project's details aren't`() =
        testApplication {
            boot()
            val owner = Fixtures.user()
            val token = Fixtures.token(owner)
            val project = Fixtures.project(owner)
            proposal(project, "Fall 2026")
            BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2027")

            val rewrite =
                client.put("/api/projects/$project/design") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "title": "Renamed",
                          "description": "Still going",
                          "designDoc": {"goal": "Rewriting history"}
                        }
                        """)
                }
            assertEquals(HttpStatusCode.BadRequest, rewrite.status)

            // Without the answers it's just the project's details, which stay editable.
            val details =
                client.put("/api/projects/$project/design") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "Renamed", "description": "Still going"}""")
                }
            assertEquals(HttpStatusCode.OK, details.status)
            val detail = details.decode<ProjectDetail>()
            assertEquals("Renamed", detail.project.title)
            assertEquals("Ship it", detail.designDocs.single().initial?.goal)
        }

    @Test
    fun `a project with no proposal can't file one over this semester's check-in`() =
        testApplication {
            boot()
            val owner = Fixtures.user()
            val token = Fixtures.token(owner)
            // No proposal at all, the way a project predating design doc rows looks.
            val project = Fixtures.project(owner)
            BoardSettings.set(BoardSettings.CURRENT_SEMESTER, "Spring 2027")

            assertEquals(
                HttpStatusCode.OK,
                client
                    .put("/api/projects/$project/semester-doc") {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody("""{"designDoc": {"goals": "Keep going"}}""")
                    }
                    .status)

            val proposal =
                client.put("/api/projects/$project/design") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"title": "T", "description": "D", "designDoc": {"goal": "Late proposal"}}
                        """)
                }
            assertEquals(HttpStatusCode.BadRequest, proposal.status)
        }

    @Test
    fun `docs stored before semesters are filed as proposals for the semester they came from`() =
        testApplication {
            boot()
            val owner = Fixtures.user()
            val token = Fixtures.token(owner)
            val project = Fixtures.project(owner)
            transaction {
                Projects.update({ Projects.id eq project }) {
                    it[legacyDesignDoc] = """{"goal": "From the old column"}"""
                    it[submittedAt] = millisAt(2025, 9)
                }
            }

            val moved = transaction { backfillInitialDesignDocs() }
            assertEquals(1, moved)
            // Running again finds nothing left to move.
            assertEquals(0, transaction { backfillInitialDesignDocs() })

            val detail =
                client.get("/api/projects/$project") { bearerAuth(token) }.decode<ProjectDetail>()
            val doc = detail.designDocs.single()
            assertEquals(DesignDocKind.INITIAL, doc.kind)
            assertEquals(semesterLabel(millisAt(2025, 9)), doc.semester)
            assertNotNull(doc.initial)
            assertEquals("From the old column", doc.initial.goal)
        }
}
