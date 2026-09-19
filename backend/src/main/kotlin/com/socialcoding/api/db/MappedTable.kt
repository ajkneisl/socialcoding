package com.socialcoding.api.db

import kotlin.reflect.KClass
import org.jetbrains.exposed.v1.core.Table

/** Map an object to the [Table] that holds it. */
@Target(AnnotationTarget.CLASS)
annotation class MappedTable(val table: KClass<out Table>)
