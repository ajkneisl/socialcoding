package com.socialcoding.projects.tasks

import com.socialcoding.api.Initializable
import com.socialcoding.api.Initialize
import com.socialcoding.api.Discord
import com.socialcoding.api.db.SqlTable
import com.socialcoding.api.db.query
import com.socialcoding.projects.Projects
import dev.kord.common.entity.Snowflake
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.toKotlinDuration
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/**
 * A log of deadline reminders already delivered, so none is ever sent twice.
 *
 * Keyed on a task's stable attributes rather than its row id: [ProjectTasks] rows are deleted and
 * re-inserted whenever a team edits their deliverables, so ids churn. Keying on the project, task
 * name, due date, and reminder [offsetDays] means re-saving unchanged deliverables won't resend,
 * while renaming or rescheduling a task earns a fresh reminder.
 */
@SqlTable
object SentReminders : Table("sent_reminders") {
    val projectID = uuid("project_id")
    val taskName = varchar("task_name", 300)
    val dueDate = varchar("due_date", 10)
    val offsetDays = integer("offset_days")

    override val primaryKey = PrimaryKey(projectID, taskName, dueDate, offsetDays)
}

/**
 * Posts deadline reminders for an active project's deliverables into its Discord channel, a week out
 * and again a day out. Runs once a day; every delivered reminder is recorded in [SentReminders] so
 * restarts and same-day re-runs stay idempotent.
 */
@Initialize
object TaskReminders : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Hour of day (server local time) at which the daily sweep runs. */
    private const val RUN_HOUR = 9

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** A means of delivering a rendered reminder. At the moment this is solely Discord. */
    fun interface ReminderDelivery {
        suspend fun send(channelID: String, content: String): Boolean
    }

    /** Delivers a reminder through Discord, resolving the stored channel id to a snowflake. */
    private val discordDelivery = ReminderDelivery { channelID, content ->
        val snowflake = channelID.toULongOrNull()?.let(::Snowflake)
        if (snowflake == null) {
            log.warn("Skipping invalid Discord channel id '$channelID'")
            false
        } else {
            Discord.sendMessage(snowflake, content)
        }
    }

    /** A reminder window: [offset] days before a due date, with the heading its message uses. */
    internal data class Window(val offset: Int, val heading: String) {
        companion object {
            /**
             * The window that fires [daysUntil] days before a due date, or null if none applies.
             *
             * Each window spans a range rather than a single day so a task added partway through
             * still gets that window's reminder on the next sweep — a deliverable created four days
             * out earns its week notice immediately instead of silently missing it.
             */
            fun of(daysUntil: Long): Window? =
                when (daysUntil) {
                    in 2..7 -> Window(7, "Due within **a week**")
                    in 0..1 -> Window(1, "Due within **a day**")
                    else -> null
                }
        }
    }

    /** A single pending reminder for one task. */
    internal data class Pending(
        val projectID: Uuid,
        val channelID: String,
        val taskName: String,
        val dueDate: String,
        val window: Window,
    )

    override suspend fun initialize() {
        scope.launch {
            while (true) {
                delay(untilNextRun())
                runCatching { runSweep(LocalDate.now(), discordDelivery) }
                    .onFailure { log.error("Task reminder sweep failed", it) }
            }
        }

        log.info("Task reminders scheduled for {}:00 daily", RUN_HOUR)
    }

    /** How long from now until the next [RUN_HOUR]:00. */
    private fun untilNextRun(): kotlin.time.Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(RUN_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toKotlinDuration()
    }

    /**
     * Delivers every reminder whose window has arrived as of [today], one message per channel,
     * recording each so it isn't repeated. Parameterized on [today] and [delivery] so tests can
     * drive it deterministically.
     */
    internal suspend fun runSweep(today: LocalDate, delivery: ReminderDelivery) {
        pruneExpired(today)

        pendingReminders(today).groupBy { it.channelID }.forEach { (channelID, reminders) ->
            if (delivery.send(channelID, buildMessage(reminders))) {
                markSent(reminders)
                log.info("Sent {} deadline reminder(s) to channel {}", reminders.size, channelID)
            }
        }
    }

    /** Drops log rows for tasks whose due date has passed; their reminders are all done. */
    private suspend fun pruneExpired(today: LocalDate) = query {
        SentReminders.deleteWhere { dueDate less today.toString() }
    }

    /** Every task whose window has arrived as of [today] and hasn't already been reminded about. */
    private suspend fun pendingReminders(today: LocalDate): List<Pending> {
        val alreadySent =
            query {
                SentReminders.selectAll().mapTo(HashSet()) {
                    reminderKey(
                        it[SentReminders.projectID],
                        it[SentReminders.taskName],
                        it[SentReminders.dueDate],
                        it[SentReminders.offsetDays],
                    )
                }
            }

        return query {
            ProjectTasks.join(Projects, JoinType.INNER, ProjectTasks.projectID, Projects.id)
                .selectAll()
                .where { (Projects.active eq true) and (ProjectTasks.dueDate neq "") }
                .mapNotNull { it.toPending(today, alreadySent) }
        }
    }

    /** Maps a joined task/project row to a [Pending] reminder, or null if none is due yet. */
    private fun ResultRow.toPending(today: LocalDate, alreadySent: Set<List<Any>>): Pending? {
        val channel = this[Projects.discordChannelId] ?: return null
        val dueDate = this[ProjectTasks.dueDate]
        val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return null
        val window = Window.of(ChronoUnit.DAYS.between(today, due)) ?: return null

        val projectID = this[ProjectTasks.projectID]
        val name = this[ProjectTasks.name]
        if (reminderKey(projectID, name, dueDate, window.offset) in alreadySent) return null

        return Pending(projectID, channel, name, dueDate, window)
    }

    /** Records every reminder in [reminders] as delivered. */
    private suspend fun markSent(reminders: List<Pending>) = query {
        reminders.forEach { r ->
            SentReminders.insert {
                it[projectID] = r.projectID
                it[taskName] = r.taskName
                it[dueDate] = r.dueDate
                it[offsetDays] = r.window.offset
            }
        }
    }

    /** Renders a channel's due tasks into a single message, grouped by window (soonest deadline last). */
    internal fun buildMessage(reminders: List<Pending>): String {
        val sections =
            reminders
                .groupBy { it.window }
                .toSortedMap(compareByDescending { it.offset })
                .map { (window, group) ->
                    val lines =
                        group.sortedBy { it.taskName }.joinToString("\n") {
                            "• ${it.taskName} — due ${it.dueDate}"
                        }
                    "${window.heading}\n$lines"
                }

        return buildString {
            appendLine("⏰ **Deliverable reminders**")
            appendLine()
            append(sections.joinToString("\n\n"))
        }
    }

    /** The dedup identity of a reminder: everything that makes it distinct. */
    private fun reminderKey(projectID: Uuid, taskName: String, dueDate: String, offset: Int) =
        listOf(projectID.toString(), taskName, dueDate, offset)
}
