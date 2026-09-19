package com.socialcoding

import com.socialcoding.api.db.Database
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Shared in-memory H2 database for the test suite. The production [com.socialcoding.api.db.Database]
 * initializer pulls credentials from the environment and also boots Discord/S3, so tests wire up
 * Exposed directly here instead — but off the same [Database.tables] discovery, so the test schema
 * can't drift from production's.
 */
object TestDatabase {
    /** Every `@SqlTable`, referenced tables first, as schema creation needs. */
    private val tables by lazy { Database.tables }

    private var connected = false

    /** Connects (once) to a persistent in-memory H2 database and creates the schema. */
    @Synchronized
    fun connect() {
        if (connected) return
        ExposedDatabase.connect(
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            user = "sa",
            password = "",
        )
        transaction { SchemaUtils.create(*tables.toTypedArray()) }
        connected = true
    }

    /** Wipes every table so a test class starts from a clean slate. */
    fun reset() {
        connect()
        // Children first, so no delete trips a foreign key.
        transaction { tables.reversed().forEach { it.deleteAll() } }
    }
}
