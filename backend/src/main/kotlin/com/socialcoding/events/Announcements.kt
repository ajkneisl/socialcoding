package com.socialcoding.events

import com.socialcoding.api.Initializable
import com.socialcoding.api.Initialize
import com.socialcoding.api.Discord
import com.socialcoding.api.Environment
import com.socialcoding.api.db.query
import com.socialcoding.board.BoardSettings
import com.socialcoding.board.BoardSettings.ANNOUNCEMENT_CHANNEL
import dev.kord.common.entity.Snowflake
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Post event announcements to the announcement channel.
 */
@Initialize
object EventAnnouncements : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    private const val RUN_HOUR = 12

    private val noon = LocalTime.of(RUN_HOUR, 0)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun initialize() {
        scope.launch {
            while (true) {
                delay(untilNextRun())
                runCatching { sweep(LocalDateTime.now()) }
                    .onFailure { log.error("Event announcement sweep failed", it) }
            }
        }

        log.info("Event announcements scheduled for {}:00 daily", RUN_HOUR)
    }

    /** How long from now until the next [RUN_HOUR]:00. */
    private fun untilNextRun(): kotlin.time.Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(noon)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toKotlinDuration()
    }

    /**
     * Posts every announcement due as of [now], returning how many went out. Parameterized on [now]
     * and [zone] so tests can drive it deterministically.
     */
    internal suspend fun sweep(
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        announcer: Announcer = discordAnnouncer,
    ): Int = query { getAllEvents() }.filter { isDue(it, now, zone) }.count { post(it, announcer) }

    /**
     * Posts [event] only if its noon has already gone by — the case where an event is published on
     * the day it happens, too late for the sweep to catch it. Otherwise the sweep will handle it.
     */
    suspend fun announceIfDue(
        event: Event,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        announcer: Announcer = discordAnnouncer,
    ): Boolean = isDue(event, now, zone) && post(event, announcer)

    /**
     * Whether [event] is waiting to be announced and its slot — noon on the day it happens — has
     * arrived. An event on any other day isn't due: a future one waits for its own sweep, and a past
     * one has missed its window for good.
     */
    private fun isDue(event: Event, now: LocalDateTime, zone: ZoneId): Boolean {
        if (!event.announce || event.announcedAt != null) return false

        val starts = Instant.ofEpochMilli(event.startsAt).atZone(zone).toLocalDateTime()
        return starts.toLocalDate() == now.toLocalDate() &&
            !now.toLocalTime().isBefore(noon)
    }

    /** A means of delivering a rendered announcement. At the moment this is solely Discord. */
    fun interface Announcer {
        suspend fun send(channelID: String, content: String): Boolean
    }

    /** Delivers an announcement through Discord, resolving the stored channel id to a snowflake. */
    private val discordAnnouncer = Announcer { channelID, content ->
        val snowflake = channelID.toULongOrNull()?.let(::Snowflake)
        if (snowflake == null) {
            log.warn("Skipping invalid announcement channel id '$channelID'")
            false
        } else {
            Discord.sendMessage(snowflake, content)
        }
    }

    /**
     * Delivers [event]'s announcement, returning whether a message went out. Stamps
     * [Event.announcedAt] on success only, so a failed post is retried by the next sweep.
     */
    private suspend fun post(event: Event, announcer: Announcer): Boolean {
        val channelID = query { BoardSettings.get(ANNOUNCEMENT_CHANNEL) }
        if (channelID.isBlank()) {
            log.info(
                "Event {} asked to be announced, but no announcement channel is configured",
                event.id,
            )
            return false
        }

        if (!announcer.send(channelID, buildMessage(event))) return false

        val announcedAt = System.currentTimeMillis()
        query {
            Events.update({ Events.id eq event.id }) {
                it[Events.announcedAt] = announcedAt
            }
        }

        log.info("Announced event {} to channel {}", event.id, channelID)
        return true
    }

    /**
     * Renders an event as an announcement.
     *
     * The date goes out as a Discord timestamp (`<t:seconds:F>`) so every member sees it in their own
     * timezone rather than the server's.
     */
    internal fun buildMessage(event: Event): String {
        val details =
            buildList {
                add("<t:${event.startsAt / 1000}:F>")
                if (event.recurring) add("repeats weekly")
                event.location?.let(::add)
            }

        return buildString {
            appendLine("📣 **${event.title}**")
            appendLine(details.joinToString(" · "))
            appendLine()
            appendLine(event.summary)
            appendLine()
            append("$siteUrl/events/${event.id}")
        }
    }

    /** Public base URL of the site, for linking announcements back to the event page. */
    private val siteUrl: String
        get() =
            Environment.getVariable("SITE_URL", "https://socialcoding.net")
                .trimEnd('/')
}
