package com.socialcoding.api.db

import com.socialcoding.board.Settings
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.EventOccurrences
import com.socialcoding.events.Events
import com.socialcoding.people.Users
import com.socialcoding.projects.ProjectLikes
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.Projects
import com.socialcoding.projects.docs.DesignDocs
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.tasks.SentReminders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.core.Table

/**
 * Schema setup is driven by reflection, so a missing `@SqlTable` shows up as a table that silently
 * never gets created rather than as a compile error. These tests are the backstop for that.
 */
class TableDiscoveryTest {
    /** Every table the app is known to have. Add here and annotate when introducing a new one. */
    private val expected =
        setOf<Table>(
            Users,
            Projects,
            ProjectMembers,
            ProjectTasks,
            ProjectLikes,
            DesignDocs,
            Events,
            EventAttendance,
            EventOccurrences,
            Settings,
            SentReminders,
        )

    @Test
    fun `discovery finds every annotated table and nothing else`() {
        assertEquals(expected, Database.tables.toSet())
    }

    @Test
    fun `every table object declared in the project carries the annotation`() {
        // Catches the real mistake: writing `object Foo : Table("foo")` and forgetting @SqlTable.
        val annotated = Database.tables.map { it.tableName }.toSet()
        val declared =
            org.reflections.Reflections("com.socialcoding")
                .getSubTypesOf(Table::class.java)
                .mapNotNull { it.kotlin.objectInstance?.tableName }
                .toSet()

        assertEquals(
            emptySet(),
            declared - annotated,
            "these tables exist but aren't annotated @SqlTable, so they'd never be created",
        )
    }

    @Test
    fun `tables come back with referenced tables first`() {
        val tables = Database.tables
        val order = tables.map { it.tableName }
        var checked = 0

        // Every foreign key must point at a table already created.
        tables.forEachIndexed { index, table ->
            table.foreignKeys.forEach { fk ->
                val target = fk.targetTable.tableName
                if (target == table.tableName) return@forEach // self-reference
                checked++
                assertTrue(
                    order.indexOf(target) < index,
                    "${table.tableName} references $target, which must be created first",
                )
            }
        }

        // Without this the test would pass just as happily on foreign keys it never saw.
        assertTrue(checked > 0, "no foreign keys were examined, so the ordering wasn't tested")
    }
}
