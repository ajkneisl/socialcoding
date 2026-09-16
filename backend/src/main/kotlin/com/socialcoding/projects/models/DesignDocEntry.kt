package com.socialcoding.projects.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One semester's design doc for a project.
 *
 * @see DesignDocKind
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
sealed class DesignDocEntry {
    /** The unique ID of the doc. */
    abstract val id: String

    /** The semester it was filed for, like `"Fall 2026"`. */
    abstract val semester: String

    /** When it was filed, in epoch ms. */
    abstract val submittedAt: Long

    /** Which type of design doc this is. */
    val kind: DesignDocKind
        get() =
            when (this) {
                is Initial -> DesignDocKind.INITIAL
                is Returning -> DesignDocKind.RETURNING
            }

    /** A project's opening proposal, filed for the semester it started in. */
    @Serializable
    @SerialName("INITIAL")
    data class Initial(
        override val id: String,
        override val semester: String,
        override val submittedAt: Long,
        val content: DesignDocContent,
    ) : DesignDocEntry()

    /** A returning project's check-in, filed at the start of every semester after its first. */
    @Serializable
    @SerialName("RETURNING")
    data class Returning(
        override val id: String,
        override val semester: String,
        override val submittedAt: Long,
        val content: ReturningDocContent,
    ) : DesignDocEntry()
}
