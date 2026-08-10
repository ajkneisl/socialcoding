package com.socialcoding.events

import com.socialcoding.api.Initializable
import com.socialcoding.api.Initialize
import com.socialcoding.api.db.query
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Keeps weekly recurring events pointed at their next meeting.
 *
 * A recurring event is one row, not a series: [Events.startsAt] always names the upcoming occurrence
 * and the weekday and time-of-day it repeats on are read straight off it. Shortly after midnight
 * every day this rolls any recurring event whose date has passed forward in whole weeks — so
 * Wednesday's 6pm meeting becomes next Wednesday at 6pm the morning after it happens — and banks the
 * finished occurrence's attendance in [EventOccurrences]. Rolling also clears [Event.announcedAt], so
 * a weekly meeting is announced afresh on each occurrence rather than only the first.
 *
 * Running a few minutes past midnight rather than at the stroke of it keeps the roll clear of any
 * clock skew around the date boundary, and leaves an event visible for the whole day it happens on.
 */
@Initialize
object RecurringEvents : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Minutes past midnight (server local time) at which the daily roll runs. */
    private const val RUN_MINUTE = 5

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun initialize() {
        scope.launch {
            while (true) {
                delay(untilNextRun())
                runCatching { rollForward(LocalDate.now()) }
                    .onFailure { log.error("Recurring event roll failed", it) }
            }
        }

        log.info(
            "Recurring event roll scheduled for 00:{} daily",
            RUN_MINUTE.toString().padStart(2, '0'),
        )
    }

    /** How long from now until the next 00:[RUN_MINUTE]. */
    private fun untilNextRun(): kotlin.time.Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(0, RUN_MINUTE)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toKotlinDuration()
    }

    /**
     * Advances every recurring event that last met before [today] onto its next weekly occurrence,
     * returning how many moved. Parameterized on [today] and [zone] so tests can drive it
     * deterministically.
     */
    internal suspend fun rollForward(
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()

        val stale = query {
            Events.selectAll()
                .where {
                    (Events.recurring eq true) and
                        (Events.startsAt less startOfToday)
                }
                .map { it[Events.id] to it[Events.startsAt] }
        }

        stale.forEach { (id, startsAt) ->
            bankOccurrence(id, startsAt)

            val next = nextOccurrence(startsAt, today, zone)
            query {
                Events.update({ Events.id eq id }) {
                    it[Events.startsAt] = next
                    // Each occurrence earns its own announcement at noon on its own day.
                    it[Events.announcedAt] = null
                }
            }

            log.info(
                "Rolled recurring event {} from {} to {}",
                id,
                Instant.ofEpochMilli(startsAt).atZone(zone),
                Instant.ofEpochMilli(next).atZone(zone),
            )
        }

        return stale.size
    }

    /**
     * The first repeat of [startsAt]'s weekday and time-of-day landing on or after [today].
     *
     * Steps in whole weeks through the event's zone, so an event keeps its wall-clock time across a
     * daylight-saving change instead of drifting by an hour.
     */
    internal fun nextOccurrence(
        startsAt: Long,
        today: LocalDate,
        zone: ZoneId,
    ): Long {
        val start = Instant.ofEpochMilli(startsAt).atZone(zone)
        val daysBehind = ChronoUnit.DAYS.between(start.toLocalDate(), today)
        if (daysBehind <= 0) return startsAt

        // Round up so the result is the soonest same-weekday date not before today.
        val weeks = (daysBehind + 6) / 7
        return start.plusWeeks(weeks).toInstant().toEpochMilli()
    }

    /**
     * Records the finished occurrence's headcount in [EventOccurrences] and clears its check-ins, so
     * members can check in again at the next meeting. A no-op when nobody attended.
     */
    private suspend fun bankOccurrence(eventID: Long, startsAt: Long): Unit = query {
        val attended =
            EventAttendance.selectAll()
                .where { EventAttendance.eventID eq eventID }
                .count()
        if (attended == 0L) return@query

        EventOccurrences.insert {
            it[EventOccurrences.eventID] = eventID
            it[EventOccurrences.startsAt] = startsAt
            it[attendees] = attended
        }
        EventAttendance.deleteWhere { EventAttendance.eventID eq eventID }
    }
}
