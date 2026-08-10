package com.socialcoding.board

import com.socialcoding.api.db.SqlTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/** The storage for all settings. These are represented thjrough a [Setting]. */
@SqlTable
object Settings : Table("settings") {
    val key = varchar("key", 64)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}

/** A database stored setting. */
class Setting<T : Any>(
    val key: String,
    val default: T,
    val decode: (String) -> T,
    val encode: (T) -> String,
) {
    companion object {
        /** A text setting, normalized by [sanitize] before storage. */
        fun text(
            key: String,
            default: String = "",
            sanitize: (String) -> String = { it.trim() },
        ) = Setting(key, default, { it }, sanitize)
    }
}

/**
 * The board-set presentation dates inherited by every project's required milestones. Stored as
 * `YYYY-MM-DD` strings (empty when unset).
 *
 * @param mvpDate The MVP Presentation date.
 * @param finalDate The Final Presentation date.
 */
@Serializable
data class PresentationDates(
    val mvpDate: String = "",
    val finalDate: String = "",
)

/**
 * Everything the board configures from its dashboard, as one payload.
 *
 * @param presentationDates The dates every project's required milestones inherit.
 * @param announcementChannelID The Discord channel events are announced to, as a channel id. Blank
 *   when unset, which turns announcing off rather than failing.
 * @param currentSemester The semester design docs are filed under. Always the effective label, so
 *   it reads the same whether it was set by hand or derived from the calendar.
 */
@Serializable
data class BoardConfig(
    val presentationDates: PresentationDates = PresentationDates(),
    val announcementChannelID: String = "",
    val currentSemester: String = "",
)

/** Reads and writes board configuration backed by [Settings]. */
object BoardSettings {
    private val log = LoggerFactory.getLogger(javaClass)

    /** MVP presentation date `YYYY-MM-DD` */
    val MVP_DATE = Setting.text("mvp_presentation_date") { it.trim().take(10) }

    /** Final presentation date `YYYY-MM-DD` */
    val FINAL_DATE = Setting.text("final_presentation_date") { it.trim().take(10) }

    /** The Discord channel events are announced to. */
    val ANNOUNCEMENT_CHANNEL =
        Setting.text("announcement_channel_id") {
            it.trim().filter(Char::isDigit).take(32)
        }

    /** The time the Discord announcements are published. */
    val ANNOUNCEMENT_TIME = Setting("announcement_time", 12, Integer::parseInt, Integer::toString)

    /** The text to show for meeting times. */
    val FOOTER = Setting.text("footer_text")

    /**
     * The semester design docs are filed under. Blank means "follow the calendar", which is the
     * normal state — see [currentSemester].
     */
    val CURRENT_SEMESTER = Setting.text("current_semester") { it.trim().take(32) }

    /** The stored value of [setting], or its default when unset or unreadable. */
    fun <T : Any> get(setting: Setting<T>): T = transaction {
        val stored =
            Settings.selectAll()
                .where { Settings.key eq setting.key }
                .singleOrNull()
                ?.get(Settings.value) ?: return@transaction setting.default

        runCatching { setting.decode(stored) }
            .onFailure {
                log.warn(
                    "Setting '{}' holds unreadable value '{}'",
                    setting.key,
                    stored,
                    it,
                )
            }
            .getOrDefault(setting.default)
    }

    /**
     * The current presentation dates. Safe to call inside an existing transaction (Exposed reuses
     * it).
     */
    fun presentationDates(): PresentationDates =
        PresentationDates(get(MVP_DATE), get(FINAL_DATE))

    /** The Discord channel events are announced to, or blank when announcing is off. */
    fun announcementChannelID(): String = get(ANNOUNCEMENT_CHANNEL)

    /**
     * The semester design docs are filed under: whatever the board pinned, or the semester today
     * falls in. Safe to call inside an existing transaction (Exposed reuses it).
     */
    fun currentSemester(): String =
        get(CURRENT_SEMESTER).ifBlank { semesterLabel(System.currentTimeMillis()) }

    /** Everything the board configures, read as one payload. */
    fun config(): BoardConfig = transaction {
        BoardConfig(presentationDates(), announcementChannelID(), currentSemester())
    }

    /**
     * Stores every value in [config]. This is a whole-payload write, so callers editing one field
     * should start from [config] and copy — sending a partial payload blanks the rest.
     */
    fun setConfig(config: BoardConfig): Unit = transaction {
        set(MVP_DATE, config.presentationDates.mvpDate)
        set(FINAL_DATE, config.presentationDates.finalDate)
        set(ANNOUNCEMENT_CHANNEL, config.announcementChannelID)
        // config() hands back the derived label when nothing is pinned, so saving that same label
        // back means "leave it on the calendar" rather than pinning this semester forever.
        val semester = config.currentSemester.trim()
        set(
            CURRENT_SEMESTER,
            if (semester == semesterLabel(System.currentTimeMillis())) "" else semester,
        )
    }

    /** Stores [value] under [setting]. */
    fun <T : Any> set(setting: Setting<T>, value: T): Unit = transaction {
        val encoded = setting.encode(value)

        val updated =
            Settings.update({ Settings.key eq setting.key }) {
                it[Settings.value] = encoded
            }

        if (updated == 0) {
            Settings.insert {
                it[Settings.key] = setting.key
                it[Settings.value] = encoded
            }
        }
    }
}
