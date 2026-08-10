package com.socialcoding.projects.models

import kotlinx.serialization.Serializable

/**
 * One semester's design doc for a project.
 *
 * The two specs ask different questions, so the answers travel as separate shapes rather than one
 * merged one: exactly one of [initial] and [returning] is filled in, matching [kind].
 *
 * @param id The unique ID of the doc.
 * @param semester The semester it was filed for, e.g. `"Fall 2026"`.
 * @param kind Which spec it answers.
 * @param submittedAt When it was filed, in epoch ms.
 * @param initial The proposal answers, when [kind] is [DesignDocKind.INITIAL].
 * @param returning The check-in answers, when [kind] is [DesignDocKind.RETURNING].
 */
@Serializable
data class DesignDocEntry(
    val id: String,
    val semester: String,
    val kind: DesignDocKind,
    val submittedAt: Long,
    val initial: DesignDocContent? = null,
    val returning: ReturningDocContent? = null,
)
