package com.socialcoding.api.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.reflections.Reflections

/**
 * Mark a SQL table.
 *
 * Read reflectively by [findTables], so a new table joins the schema by carrying this annotation —
 * there's no central list to remember to update. Named `SqlTable` rather than `Table` because every
 * file that would use it also declares an Exposed [Table], and two `Table`s in one file don't resolve.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SqlTable
