package com.socialcoding.db

/** Map a field to a specific [column]. */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MapsTo(val column: String)
