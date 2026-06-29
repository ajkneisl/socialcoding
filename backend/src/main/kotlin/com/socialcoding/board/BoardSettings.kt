package com.socialcoding.board

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Simple key/value store for board-wide configuration. */
object Settings : Table("settings") {
    val key = varchar("key", 64)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}

/**
 * The board-set presentation dates inherited by every project's required milestones. Stored as
 * `YYYY-MM-DD` strings (empty when unset).
 *
 * @param mvpDate The MVP Presentation date.
 * @param finalDate The Final Presentation date.
 */
@Serializable data class PresentationDates(val mvpDate: String = "", val finalDate: String = "")

/** Reads and writes board configuration backed by [Settings]. */
object BoardSettings {
    private const val MVP_KEY = "mvp_presentation_date"
    private const val FINAL_KEY = "final_presentation_date"

    /** The current presentation dates. Safe to call inside an existing transaction (Exposed reuses it). */
    fun presentationDates(): PresentationDates = transaction {
        val stored = Settings.selectAll().associate { it[Settings.key] to it[Settings.value] }
        PresentationDates(stored[MVP_KEY] ?: "", stored[FINAL_KEY] ?: "")
    }

    fun setPresentationDates(dates: PresentationDates) = transaction {
        put(MVP_KEY, dates.mvpDate.trim().take(10))
        put(FINAL_KEY, dates.finalDate.trim().take(10))
    }

    /** Upsert a single key. Must be inside a transaction. */
    private fun put(key: String, value: String) {
        val updated = Settings.update({ Settings.key eq key }) { it[Settings.value] = value }
        if (updated == 0) {
            Settings.insert {
                it[Settings.key] = key
                it[Settings.value] = value
            }
        }
    }
}
