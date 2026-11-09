package com.socialcoding.board

import com.socialcoding.api.db.SqlTable
import com.socialcoding.common.APIError
import com.socialcoding.people.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Employers the board has made a decision about. A company that only appears on member profiles
 * has no row here, which is what makes it hidden until the board shows it.
 */
@SqlTable
object Employers : Table("employers") {
    val key = varchar("key", 255)
    val name = varchar("name", 255)
    val visible = bool("visible").default(false)

    override val primaryKey = PrimaryKey(key)
}

/**
 * An employer as the board sees it.
 *
 * @param key The normalized identity, see [employerKey].
 * @param name The display name.
 * @param visible Whether it appears on the public home page.
 * @param memberCount How many people on the public People page list it.
 */
@Serializable
data class Employer(
    val key: String,
    val name: String,
    val visible: Boolean,
    val memberCount: Int,
)

/** Loose identity, so "Two Sigma", "TwoSigma" and "two sigma" are one employer. */
fun employerKey(name: String): String = name.filter(Char::isLetterOrDigit).lowercase()

/** The board-curated employer list, merged with what members put on their profiles. */
object EmployerDirectory {
    /** The employers shown before they were board-managed. Only used by [seedOnce]. */
    private val PAST_EMPLOYERS =
        listOf(
            "Jane Street",
            "Cognition",
            "Coinbase",
            "Riot Games",
            "Tesla",
            "NASA",
            "Meta",
            "Stripe",
            "IBM",
            "Two Sigma",
        )

    /** Every employer on a public profile. Unlisted members opted out of being surfaced. */
    private fun profileEmployers(): List<String> =
        Users.selectAll()
            .where { Users.listed eq true }
            .mapNotNull { it[Users.company]?.trim() }
            .filter { employerKey(it).isNotEmpty() }

    private fun requireKey(name: String): String =
        employerKey(name).ifEmpty { throw APIError("An employer needs a name") }

    /** Everything the board can manage, stored or only sourced from profiles. */
    fun list(): List<Employer> = transaction {
        val sourced = profileEmployers().groupBy(::employerKey)
        val stored = Employers.selectAll().associateBy { it[Employers.key] }

        (stored.keys + sourced.keys)
            .map { key ->
                val row = stored[key]
                val spellings = sourced[key].orEmpty()
                Employer(
                    key = key,
                    name = row?.get(Employers.name) ?: spellings.min(),
                    visible = row?.get(Employers.visible) ?: false,
                    memberCount = spellings.size,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** The names cleared for the public home page. */
    fun visibleNames(): List<String> = transaction {
        Employers.selectAll()
            .where { Employers.visible eq true }
            .map { it[Employers.name] }
            .sortedBy { it.lowercase() }
    }

    /** Adds [name], hidden, unless that employer is already stored. */
    fun add(name: String): Unit = transaction {
        val key = requireKey(name)
        if (Employers.selectAll().where { Employers.key eq key }.empty()) insert(key, name, false)
    }

    /** Shows or hides [name], storing it first if it was only sourced from profiles. */
    fun setVisible(name: String, visible: Boolean): Unit = transaction {
        val key = requireKey(name)
        val updated = Employers.update({ Employers.key eq key }) { it[Employers.visible] = visible }
        if (updated == 0) insert(key, name, visible)
    }

    /** Forgets [key]. If members still list it, it comes back as a hidden profile employer. */
    fun remove(key: String): Unit = transaction {
        Employers.deleteWhere { Employers.key eq key }
    }

    /**
     * Carries over what the home page showed before the board managed it, so deploying this doesn't
     * empty the marquee. Runs once per database; later profile employers start out hidden.
     */
    fun seedOnce(): Unit = transaction {
        if (BoardSettings.get(BoardSettings.EMPLOYERS_SEEDED)) return@transaction

        (PAST_EMPLOYERS + profileEmployers().sorted())
            .distinctBy(::employerKey)
            .forEach { setVisible(it, true) }

        BoardSettings.set(BoardSettings.EMPLOYERS_SEEDED, true)
    }

    private fun insert(key: String, name: String, visible: Boolean) {
        Employers.insert {
            it[Employers.key] = key
            it[Employers.name] = name.trim().take(255)
            it[Employers.visible] = visible
        }
    }
}
