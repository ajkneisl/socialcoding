package com.socialcoding.projects

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.board.PresentationDates
import com.socialcoding.people.Role
import com.socialcoding.projects.docs.decodeDesignDoc
import com.socialcoding.projects.docs.encodeDesignDoc
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.models.ProjectStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ProjectQueriesTest {

    private val dates = PresentationDates(mvpDate = "2026-09-01", finalDate = "2026-12-01")

    @BeforeTest fun clean() = TestDatabase.reset()

    // --- Presentation milestones ------------------------------------------------------------

    @Test
    fun `both presentation milestones are appended when a team submits neither`() {
        val result = withPresentationMilestones(listOf(TaskInput(name = "Build it")), dates)

        assertEquals(listOf("Build it", "MVP Presentation", "Final Presentation"), result.map { it.name })
        // The team's own deliverable is left exactly as submitted.
        assertFalse(result.first().milestone)
        // The appended milestones carry the board's dates.
        assertEquals("2026-09-01", result[1].dueDate)
        assertEquals("2026-12-01", result[2].dueDate)
        assertTrue(result.drop(1).all { it.milestone })
    }

    @Test
    fun `an existing milestone is replaced rather than kept or duplicated`() {
        val result =
            withPresentationMilestones(
                listOf(TaskInput(name = "  mvp presentation  ", dueDate = "2026-01-01")),
                dates,
            )

        assertEquals(listOf("MVP Presentation", "Final Presentation"), result.map { it.name })
        // Last semester's date is gone: the pair is filed fresh off the board's current dates.
        assertEquals("2026-09-01", result.first().dueDate)
        assertTrue(result.first().milestone)
    }

    @Test
    fun `replaced milestones move to the end of the list`() {
        val result =
            withPresentationMilestones(
                listOf(
                    TaskInput(name = "Final Presentation"),
                    TaskInput(name = "Build it"),
                ),
                dates,
            )

        // The team's deliverable keeps its order; both milestones are re-filed after it.
        assertEquals(
            listOf("Build it", "MVP Presentation", "Final Presentation"),
            result.map { it.name },
        )
    }

    @Test
    fun `dependencies are remapped around a removed milestone`() {
        val result =
            withPresentationMilestones(
                listOf(
                    TaskInput(name = "MVP Presentation"),
                    TaskInput(name = "Design"),
                    TaskInput(name = "Build it", dependsOn = listOf(0, 1)),
                ),
                dates,
            )

        assertEquals(listOf("Design", "Build it", "MVP Presentation", "Final Presentation"), result.map { it.name })
        // "Build it" depended on the dropped milestone and on "Design"; only "Design" survives,
        // and it has shifted from index 1 to index 0.
        assertEquals(listOf(0), result[1].dependsOn)
    }

    @Test
    fun `unset board dates still produce the milestones`() {
        val result = withPresentationMilestones(emptyList(), PresentationDates())

        assertEquals(listOf("MVP Presentation", "Final Presentation"), result.map { it.name })
        assertTrue(result.all { it.dueDate.isEmpty() }, "no date until the board sets one")
    }

    // --- Task storage -----------------------------------------------------------------------

    @Test
    fun `dependencies survive a round trip as list indices`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        transaction {
            replaceTasks(
                project,
                listOf(
                    TaskInput(name = "Design", dueDate = "2026-01-01"),
                    TaskInput(name = "Build", dueDate = "2026-02-01", dependsOn = listOf(0)),
                    TaskInput(name = "Ship", dueDate = "2026-03-01", dependsOn = listOf(0, 1)),
                ),
                setOf(owner),
            )
        }

        val inputs = transaction { currentTaskInputs(project) }
        assertEquals(listOf("Design", "Build", "Ship"), inputs.map { it.name })
        assertEquals(emptyList(), inputs[0].dependsOn)
        assertEquals(listOf(0), inputs[1].dependsOn)
        assertEquals(listOf(0, 1), inputs[2].dependsOn)
    }

    @Test
    fun `a task cannot depend on itself and unknown indices are dropped`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        transaction {
            replaceTasks(
                project,
                listOf(
                    TaskInput(name = "First", dueDate = "2026-01-01"),
                    TaskInput(name = "Second", dueDate = "2026-02-01", dependsOn = listOf(1, 0, 99)),
                ),
                setOf(owner),
            )
        }

        val inputs = transaction { currentTaskInputs(project) }
        assertEquals(listOf(0), inputs[1].dependsOn, "self and out-of-range references are discarded")
    }

    @Test
    fun `assignees who aren't on the team are dropped`() {
        val owner = Fixtures.user()
        val outsider = Fixtures.user()
        val project = Fixtures.project(owner)

        transaction {
            replaceTasks(
                project,
                listOf(
                    TaskInput(
                        name = "Build",
                        assigneeIds = listOf(owner.toString(), outsider.toString(), "not-a-uuid"),
                    )
                ),
                setOf(owner),
            )
        }

        val inputs = transaction { currentTaskInputs(project) }
        assertEquals(listOf(owner.toString()), inputs.single().assigneeIds)
    }

    @Test
    fun `replacing tasks clears whatever was there before`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.task(project, "Old deliverable", dueDate = "2026-01-01")

        transaction { replaceTasks(project, listOf(TaskInput(name = "New deliverable")), setOf(owner)) }

        assertEquals(listOf("New deliverable"), transaction { currentTaskInputs(project) }.map { it.name })
    }

    @Test
    fun `overlong names and dates are truncated to what the columns hold`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        transaction {
            replaceTasks(
                project,
                listOf(TaskInput(name = "  " + "x".repeat(400) + "  ", dueDate = "2026-01-01T00:00")),
                setOf(owner),
            )
        }

        val task = transaction { currentTaskInputs(project) }.single()
        assertEquals(300, task.name.length)
        assertEquals("2026-01-01", task.dueDate)
    }

    @Test
    fun `stamping swaps last semester's milestones for the board's current dates`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)
        transaction {
            replaceTasks(
                project,
                withPresentationMilestones(listOf(TaskInput(name = "Build it", dueDate = "2026-05-01")), dates),
                setOf(owner),
            )
        }

        val moved = PresentationDates(mvpDate = "2026-10-15", finalDate = "2027-01-15")
        transaction { stampPresentationMilestones(project, moved) }

        val tasks = transaction { currentTaskInputs(project) }
        val byName = tasks.associateBy { it.name }
        assertEquals(3, tasks.size, "the old pair is removed, not left alongside the new one")
        assertEquals("2026-10-15", byName.getValue("MVP Presentation").dueDate)
        assertEquals("2027-01-15", byName.getValue("Final Presentation").dueDate)
        assertEquals("2026-05-01", byName.getValue("Build it").dueDate, "the team's own date is untouched")
    }

    @Test
    fun `stamping adds the milestones to a project that never had them`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.task(project, "Only deliverable", dueDate = "2026-05-01")

        transaction { stampPresentationMilestones(project, dates) }

        val names = transaction { currentTaskInputs(project) }.map { it.name }
        assertContains(names, "MVP Presentation")
        assertContains(names, "Final Presentation")
        assertContains(names, "Only deliverable")
    }

    @Test
    fun `blank task names never reach storage`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)

        transaction {
            replaceTasks(
                project,
                listOf(
                    TaskInput(name = "   ", dueDate = "2026-01-01"),
                    TaskInput(name = "Real", dueDate = "2026-02-01"),
                    TaskInput(name = "Later", dueDate = "2026-03-01", dependsOn = listOf(1)),
                ),
                setOf(owner),
            )
        }

        val tasks = transaction { currentTaskInputs(project) }
        assertEquals(listOf("Real", "Later"), tasks.map { it.name })
        // "Later" pointed at index 1 ("Real"), which shifted to index 0 when the blank was dropped.
        assertEquals(listOf(0), tasks[1].dependsOn)
    }

    // --- Design doc encoding ----------------------------------------------------------------

    @Test
    fun `an unset or unreadable design doc decodes to blank answers`() {
        assertEquals(DesignDocContent(), decodeDesignDoc(null))
        assertEquals(DesignDocContent(), decodeDesignDoc("not json at all"))
    }

    @Test
    fun `design doc answers round trip and unknown keys are tolerated`() {
        val doc = DesignDocContent(goal = "Ship a test", softwareStack = "Ktor, React")
        assertEquals(doc, decodeDesignDoc(encodeDesignDoc(doc)))

        // Answers dropped from the model in a later revision mustn't break an existing row.
        assertEquals("Ship a test", decodeDesignDoc("""{"goal":"Ship a test","retired":"x"}""").goal)
    }

    @Test
    fun `blank answers are written out rather than omitted`() {
        // The editor binds to every field, so they have to be present in the response.
        assertContains(encodeDesignDoc(DesignDocContent()), "\"goal\"")
    }

    // --- Visibility -------------------------------------------------------------------------

    @Test
    fun `a pending project is visible to its team and the board, and nobody else`() {
        val owner = Fixtures.user()
        val member = Fixtures.user()
        val invitee = Fixtures.user()
        val outsider = Fixtures.user()
        val project = Fixtures.project(owner, status = ProjectStatus.PENDING)
        Fixtures.member(project, member)
        Fixtures.invite(project, invitee, MemberStatus.PENDING)

        assertNotNull(projectDetail(project, owner, Role.MEMBER))
        assertNotNull(projectDetail(project, member, Role.MEMBER))
        assertNotNull(projectDetail(project, outsider, Role.BOARD))
        assertNull(projectDetail(project, outsider, Role.MEMBER))
        assertNull(projectDetail(project, invitee, Role.MEMBER), "an invite isn't access yet")
    }

    @Test
    fun `an approved project is visible to any signed-in user`() {
        val owner = Fixtures.user()
        val outsider = Fixtures.user()
        val project = Fixtures.project(owner, status = ProjectStatus.APPROVED)

        val detail = assertNotNull(projectDetail(project, outsider, Role.MEMBER))
        assertFalse(detail.canEdit, "seeing it doesn't mean editing it")
        assertFalse(detail.canManageTeam)
    }

    @Test
    fun `permissions follow the team lead and the board`() {
        val owner = Fixtures.user()
        val lead = Fixtures.user()
        val member = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.lead(project, lead)
        Fixtures.member(project, member)

        val asLead = assertNotNull(projectDetail(project, lead, Role.MEMBER))
        assertTrue(asLead.canEdit)
        assertTrue(asLead.canManageTeam)

        val asMember = assertNotNull(projectDetail(project, member, Role.MEMBER))
        assertTrue(asMember.canEdit, "any teammate may edit the doc")
        assertFalse(asMember.canManageTeam, "only the lead changes the team")

        val asBoard = assertNotNull(projectDetail(project, Fixtures.user(), Role.BOARD))
        assertTrue(asBoard.canManageTeam)
    }

    @Test
    fun `detail separates accepted members from outstanding invites`() {
        val owner = Fixtures.user(name = "Owner")
        val member = Fixtures.user(name = "Member")
        val invitee = Fixtures.user(name = "Invitee")
        val project = Fixtures.project(owner)
        Fixtures.member(project, member)
        Fixtures.invite(project, invitee, MemberStatus.PENDING)

        val detail = assertNotNull(projectDetail(project, owner, Role.MEMBER))
        // The lead (here the owner, since none is set) is always listed with the team.
        assertEquals(listOf("Member", "Owner"), detail.members.map { it.name })
        assertEquals(listOf("Invitee"), detail.pendingMembers.map { it.name })
        assertEquals(owner.toString(), detail.teamLeadID)
    }

    @Test
    fun `a missing project has no detail`() {
        assertNull(projectDetail(Uuid.random(), Fixtures.user(), Role.BOARD))
    }

    // --- Public listings --------------------------------------------------------------------

    @Test
    fun `only approved projects are listed publicly`() {
        val owner = Fixtures.user()
        Fixtures.project(owner, status = ProjectStatus.APPROVED, title = "Shipped")
        Fixtures.project(owner, status = ProjectStatus.PENDING, title = "In review")
        Fixtures.project(owner, status = ProjectStatus.REJECTED, title = "Turned down")

        assertEquals(listOf("Shipped"), listApprovedProjects().map { it.title })
    }

    @Test
    fun `the listing is ordered by hearts, newest breaking ties`() {
        val owner = Fixtures.user()
        val fan = Fixtures.user()
        val other = Fixtures.user()
        val popular = Fixtures.project(owner, ProjectStatus.APPROVED, title = "Popular", submittedAt = 1)
        Fixtures.project(owner, ProjectStatus.APPROVED, title = "Older", submittedAt = 2)
        Fixtures.project(owner, ProjectStatus.APPROVED, title = "Newer", submittedAt = 3)
        Fixtures.like(popular, fan)
        Fixtures.like(popular, other)

        val listed = listApprovedProjects()
        // Hearts win outright: the oldest project leads because it has two.
        assertEquals(listOf("Popular", "Newer", "Older"), listed.map { it.title })
        assertEquals(listOf(2L, 0L, 0L), listed.map { it.likes })
    }

    @Test
    fun `the viewer's own hearts are marked, and an anonymous viewer's aren't`() {
        val owner = Fixtures.user()
        val fan = Fixtures.user()
        val project = Fixtures.project(owner, ProjectStatus.APPROVED)
        Fixtures.like(project, fan)

        assertTrue(listApprovedProjects(fan).single().liked)
        assertFalse(listApprovedProjects(owner).single().liked)
        assertFalse(listApprovedProjects(null).single().liked)
    }

    @Test
    fun `the showcase is only built for approved projects`() {
        val owner = Fixtures.user(name = "Owner")
        val member = Fixtures.user(name = "Member")
        val approved = Fixtures.project(owner, ProjectStatus.APPROVED, title = "Live")
        Fixtures.member(approved, member)
        val pending = Fixtures.project(owner, ProjectStatus.PENDING)

        val showcase = assertNotNull(projectShowcase(approved, null))
        assertEquals("Live", showcase.project.title)
        assertEquals(owner.toString(), showcase.teamLeadID)
        assertEquals(listOf("Member", "Owner"), showcase.members.map { it.name })

        assertNull(projectShowcase(pending, null))
        assertNull(projectShowcase(Uuid.random(), null))
    }

    @Test
    fun `the review queue lists unapproved projects, pending ones first`() {
        val owner = Fixtures.user()
        Fixtures.project(owner, ProjectStatus.REJECTED, title = "Turned down", submittedAt = 1)
        Fixtures.project(owner, ProjectStatus.PENDING, title = "Waiting", submittedAt = 2)
        Fixtures.project(owner, ProjectStatus.APPROVED, title = "Done", submittedAt = 3)

        assertEquals(listOf("Waiting", "Turned down"), getPendingProjects().map { it.project.title })
    }

    // --- A user's own projects --------------------------------------------------------------

    @Test
    fun `a user's projects cover ownership, leading and membership but not invites`() {
        val user = Fixtures.user()
        val someoneElse = Fixtures.user()

        val owned = Fixtures.project(user, title = "Owned", submittedAt = 1)
        val led = Fixtures.project(someoneElse, title = "Led", submittedAt = 2)
        Fixtures.lead(led, user)
        val joined = Fixtures.project(someoneElse, title = "Joined", submittedAt = 3)
        Fixtures.member(joined, user)
        val invited = Fixtures.project(someoneElse, title = "Invited", submittedAt = 4)
        Fixtures.invite(invited, user, MemberStatus.PENDING)
        Fixtures.project(someoneElse, title = "Unrelated", submittedAt = 5)

        assertEquals(listOf("Owned", "Led", "Joined"), projectsForUser(user).map { it.title })
        assertEquals(listOf(owned, led, joined).map { it.toString() }, projectsForUser(user).map { it.id })
    }

    @Test
    fun `a user's projects include ones the board hasn't approved`() {
        val user = Fixtures.user()
        Fixtures.project(user, ProjectStatus.PENDING, title = "In review")
        Fixtures.project(user, ProjectStatus.REJECTED, title = "Turned down")

        assertEquals(setOf("In review", "Turned down"), projectsForUser(user).map { it.title }.toSet())
    }

    @Test
    fun `invites list only outstanding ones`() {
        val user = Fixtures.user()
        val owner = Fixtures.user()
        val invited = Fixtures.project(owner, title = "Invited")
        Fixtures.invite(invited, user, MemberStatus.PENDING)
        val accepted = Fixtures.project(owner, title = "Accepted")
        Fixtures.member(accepted, user)

        assertEquals(listOf("Invited"), invitesForUser(user).map { it.title })
        assertEquals(emptyList(), invitesForUser(Fixtures.user()))
    }

    // --- Row mapping ------------------------------------------------------------------------

    @Test
    fun `a project without a team lead shows the owner instead`() {
        val owner = Fixtures.user(name = "Owner", avatarUrl = "https://example.test/owner.png")
        Fixtures.project(owner, ProjectStatus.APPROVED)

        val project = listApprovedProjects().single()
        assertEquals("Owner", project.teamLeadName)
        assertEquals("https://example.test/owner.png", project.teamLeadAvatarUrl)
    }

    @Test
    fun `a project with a team lead shows the lead`() {
        val owner = Fixtures.user(name = "Owner")
        val lead = Fixtures.user(name = "Lead", avatarUrl = "https://example.test/lead.png")
        val project = Fixtures.project(owner, ProjectStatus.APPROVED)
        Fixtures.lead(project, lead)

        val listed = listApprovedProjects().single()
        assertEquals("Lead", listed.teamLeadName)
        assertEquals("https://example.test/lead.png", listed.teamLeadAvatarUrl)
    }
}
