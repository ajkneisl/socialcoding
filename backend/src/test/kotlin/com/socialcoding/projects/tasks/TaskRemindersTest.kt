package com.socialcoding.projects.tasks

import com.socialcoding.TestDatabase
import com.socialcoding.people.Users
import com.socialcoding.projects.Projects
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private class FakeSink(private val succeed: Boolean = true) :
    TaskReminders.ReminderDelivery {
    val messages = mutableListOf<Pair<String, String>>()

    override suspend fun send(channelID: String, content: String): Boolean {
        messages.add(channelID to content)
        return succeed
    }

    /** All messages posted to [channelID]. */
    fun to(channelID: String) =
        messages.filter { it.first == channelID }.map { it.second }
}

class TaskRemindersTest {
    private val today = LocalDate.parse("2026-07-27")
    private val emailCounter = AtomicInteger()

    @BeforeTest fun clean() = TestDatabase.reset()

    @Test
    fun `windowFor maps day counts to the right window`() {
        assertNull(TaskReminders.Window.of(9), "far future gets no reminder")
        assertNull(
            TaskReminders.Window.of(8),
            "8 days out is just past the week window",
        )
        assertEquals(7, TaskReminders.Window.of(7)?.offset)
        assertEquals(
            7,
            TaskReminders.Window.of(2)?.offset,
            "2 days still falls in the week window",
        )
        assertEquals(
            1,
            TaskReminders.Window.of(1)?.offset,
            "the day before is the day window",
        )
        assertEquals(
            1,
            TaskReminders.Window.of(0)?.offset,
            "a task due today still lands in the day window",
        )
        assertNull(TaskReminders.Window.of(-1), "overdue gets no reminder")
    }

    @Test
    fun `buildMessage groups tasks by window, most-distant first, names sorted`() {
        val project = Uuid.random()
        val message =
            TaskReminders.buildMessage(
                listOf(
                    pending(project, "Zebra task", "2026-07-28", 1),
                    pending(project, "Apple task", "2026-07-28", 1),
                    pending(project, "Week task", "2026-08-03", 7),
                )
            )

        assertContains(message, "Deliverable reminders")
        assertContains(
            message,
            "Due within **a week**\n• Week task — due 2026-08-03",
        )
        // Two tasks due within a day, alphabetized under one heading.
        assertContains(
            message,
            "Due within **a day**\n• Apple task — due 2026-07-28\n• Zebra task — due 2026-07-28",
        )
        // The week-out window comes before the day-out one.
        assertTrue(message.indexOf("a week") < message.indexOf("a day"))
    }

    // --- Sweep behaviour
    // --------------------------------------------------------------------

    @Test
    fun `sweep sends one grouped message covering each reminder window`() =
        runBlocking {
            val channel = "1001"
            val project = seedProject(channel = channel)
            seedTask(project, "Week deliverable", "2026-08-03") // 7 days
            seedTask(
                project,
                "Midweek deliverable",
                "2026-07-29",
            ) // 2 days — still the week window
            seedTask(project, "Tomorrow deliverable", "2026-07-28") // 1 day
            seedTask(project, "Today deliverable", "2026-07-27") // 0 days
            seedTask(
                project,
                "Far deliverable",
                "2026-08-10",
            ) // 14 days — no reminder
            seedTask(
                project,
                "Overdue deliverable",
                "2026-07-20",
            ) // past — no reminder
            seedTask(
                project,
                "Undated deliverable",
                "",
            ) // no due date — no reminder

            val sink = FakeSink()
            TaskReminders.runSweep(today, sink)

            val messages = sink.to(channel)
            assertEquals(1, messages.size, "one grouped message per channel")
            val body = messages.single()
            assertContains(body, "Week deliverable")
            assertContains(body, "Midweek deliverable")
            assertContains(body, "Tomorrow deliverable")
            assertContains(body, "Today deliverable")
            assertFalse(body.contains("Far deliverable"))
            assertFalse(body.contains("Overdue deliverable"))
            assertFalse(body.contains("Undated deliverable"))
        }

    @Test
    fun `sweep is idempotent - a second run sends nothing new`() = runBlocking {
        val channel = "1002"
        val project = seedProject(channel = channel)
        seedTask(project, "Ship it", "2026-07-29")

        val first = FakeSink()
        TaskReminders.runSweep(today, first)
        assertEquals(1, first.to(channel).size)

        val second = FakeSink()
        TaskReminders.runSweep(today, second)
        assertTrue(
            second.messages.isEmpty(),
            "already-sent reminder is not repeated",
        )
    }

    @Test
    fun `the day-out window fires even after the week-out one was sent`() =
        runBlocking {
            val channel = "1003"
            val project = seedProject(channel = channel)
            seedTask(project, "Milestone", "2026-07-29")

            // Five days out: the week window fires.
            val weekSink = FakeSink()
            TaskReminders.runSweep(LocalDate.parse("2026-07-24"), weekSink)
            assertContains(
                weekSink.to(channel).single(),
                "Due within **a week**",
            )

            // A sweep in between adds nothing — the week window is spent.
            val quietSink = FakeSink()
            TaskReminders.runSweep(LocalDate.parse("2026-07-26"), quietSink)
            assertTrue(quietSink.messages.isEmpty())

            // The day before: a distinct window, so a second reminder goes out.
            val soonSink = FakeSink()
            TaskReminders.runSweep(LocalDate.parse("2026-07-28"), soonSink)
            assertContains(soonSink.to(channel).single(), "Due within **a day**")

            // On the due date itself the day window has already been used up,
            // so a task earns exactly the two reminders that were asked for.
            val dueSink = FakeSink()
            TaskReminders.runSweep(LocalDate.parse("2026-07-29"), dueSink)
            assertTrue(dueSink.messages.isEmpty())
            assertEquals(2, sentReminderCount())
        }

    @Test
    fun `inactive projects get no reminders`() = runBlocking {
        val channel = "1004"
        val project = seedProject(channel = channel, active = false)
        seedTask(project, "Task", "2026-07-29")

        val sink = FakeSink()
        TaskReminders.runSweep(today, sink)
        assertTrue(sink.messages.isEmpty())
    }

    @Test
    fun `projects without a discord channel are skipped`() = runBlocking {
        val project = seedProject(channel = null)
        seedTask(project, "Task", "2026-07-29")

        val sink = FakeSink()
        TaskReminders.runSweep(today, sink)
        assertTrue(sink.messages.isEmpty())
    }

    @Test
    fun `a failed delivery is not logged, so the next sweep retries`() =
        runBlocking {
            val channel = "1005"
            val project = seedProject(channel = channel)
            seedTask(project, "Retry me", "2026-07-29")

            // First delivery fails: nothing should be recorded as sent.
            TaskReminders.runSweep(today, FakeSink(succeed = false))
            assertEquals(0, sentReminderCount())

            // Next sweep succeeds and resends.
            val retry = FakeSink(succeed = true)
            TaskReminders.runSweep(today, retry)
            assertContains(retry.to(channel).single(), "Retry me")
            assertEquals(1, sentReminderCount())
        }

    @Test
    fun `each project's reminders go to its own channel`() = runBlocking {
        val projectA = seedProject(channel = "2001")
        val projectB = seedProject(channel = "2002")
        seedTask(projectA, "A task", "2026-07-29")
        seedTask(projectB, "B task", "2026-07-29")

        val sink = FakeSink()
        TaskReminders.runSweep(today, sink)

        assertContains(sink.to("2001").single(), "A task")
        assertFalse(sink.to("2001").single().contains("B task"))
        assertContains(sink.to("2002").single(), "B task")
        assertFalse(sink.to("2002").single().contains("A task"))
    }

    @Test
    fun `rescheduling a task earns a fresh reminder`() = runBlocking {
        val channel = "1006"
        val project = seedProject(channel = channel)
        seedTask(project, "Movable", "2026-07-29")

        TaskReminders.runSweep(today, FakeSink())
        assertEquals(1, sentReminderCount())

        // Team pushes the deadline back; the new due date is a new reminder
        // identity.
        transaction {
            ProjectTasks.deleteWhere { ProjectTasks.projectID eq project }
        }
        seedTask(project, "Movable", "2026-08-03")

        val afterReschedule = FakeSink()
        TaskReminders.runSweep(today, afterReschedule)
        assertContains(afterReschedule.to(channel).single(), "due 2026-08-03")
    }

    @Test
    fun `past-due reminder logs are cleaned up`() = runBlocking {
        val channel = "1007"
        val project = seedProject(channel = channel)
        seedTask(project, "Due today", "2026-07-27")

        TaskReminders.runSweep(today, FakeSink())
        assertEquals(1, sentReminderCount())

        // A sweep run a week later, once the deadline has passed, prunes the
        // stale log row.
        TaskReminders.runSweep(LocalDate.parse("2026-08-05"), FakeSink())
        assertEquals(0, sentReminderCount())
    }

    // --- Helpers
    // ----------------------------------------------------------------------------

    private fun pending(
        project: Uuid,
        name: String,
        dueDate: String,
        offset: Int,
    ) =
        TaskReminders.Pending(
            projectID = project,
            channelID = "chan",
            taskName = name,
            dueDate = dueDate,
            window = TaskReminders.Window.of(offset.toLong())!!,
        )

    private fun seedProject(channel: String?, active: Boolean = true): Uuid =
        transaction {
            val owner =
                Users.insert {
                    it[email] =
                        "owner${emailCounter.incrementAndGet()}@test.edu"
                    it[name] = "Owner"
                    it[createdAt] = 0
                } get Users.id

            Projects.insert {
                it[title] = "Project"
                it[description] = "A test project"
                it[ownerId] = owner
                it[this.active] = active
                it[discordChannelId] = channel
                it[submittedAt] = 0
            } get Projects.id
        }

    private fun seedTask(project: Uuid, name: String, dueDate: String) =
        transaction {
            ProjectTasks.insert {
                it[projectID] = project
                it[this.name] = name
                it[this.dueDate] = dueDate
            }
        }

    private fun sentReminderCount(): Int = transaction {
        SentReminders.selectAll().count().toInt()
    }
}
