package com.socialcoding.projects.docs

import com.socialcoding.api.db.SqlTable
import com.socialcoding.projects.Projects
import com.socialcoding.projects.models.DesignDocContent
import com.socialcoding.projects.models.DesignDocEntry
import com.socialcoding.projects.models.DesignDocKind
import com.socialcoding.projects.models.ReturningDocContent
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * A design doc corresponding to a project's semester.
 *
 * @see com.socialcoding.projects.models.Project
 */
@SqlTable
object DesignDocs : Table("design_docs") {
    /** A unique ID of the design doc. */
    val id = uuid("id").clientDefault { Uuid.random() }

    /** The ID of the corresponding project ID. */
    val projectID = uuid("project_id").references(Projects.id)

    /** The semester that the doc was filled out for. */
    val semester = varchar("semester", 32)

    /** If the design doc is initial or returning. */
    val kind = enumerationByName("kind", 16, DesignDocKind::class).default(DesignDocKind.INITIAL)

    /** The JSON content, corresponding to the [kind]. */
    val content = text("content")

    /** In epoch ms when the design doc was submitted. */
    val submittedAt = long("submitted_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(projectID, semester)
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Decode the content of an initial design doc. If the contents are invalid, a blank doc is
 * returned.
 *
 * @see DesignDocs.content
 * @see DesignDocContent
 */
fun decodeDesignDoc(raw: String?): DesignDocContent =
    raw?.let { runCatching { json.decodeFromString<DesignDocContent>(it) }.getOrNull() }
        ?: DesignDocContent()

/**
 * Encode an initial design doc to a JSON string.
 *
 * @see DesignDocs.content
 * @see DesignDocContent
 */
fun encodeDesignDoc(doc: DesignDocContent): String =
    json.encodeToString(DesignDocContent.serializer(), doc)

/**
 * Decode the content of a returning design doc. If the contents are invalid, a blank doc is
 * returned.
 *
 * @see DesignDocs.content
 * @see ReturningDocContent
 */
fun decodeReturningDoc(raw: String?): ReturningDocContent =
    raw?.let { runCatching { json.decodeFromString<ReturningDocContent>(it) }.getOrNull() }
        ?: ReturningDocContent()

/**
 * Encode a returning design doc to a JSON string.
 *
 * @see DesignDocs.content
 * @see ReturningDocContent
 */
fun encodeReturningDoc(doc: ReturningDocContent): String =
    json.encodeToString(ReturningDocContent.serializer(), doc)

/**
 * Reads a row as the entry clients see, decoding [DesignDocs.content] against [DesignDocs.kind].
 *
 * The column is the stored discriminator, so it picks the shape here once; from this point on the
 * type carries the kind and nothing has to check it again.
 */
fun ResultRow.toDesignDocEntry(): DesignDocEntry {
    val id = this[DesignDocs.id].toString()
    val semester = this[DesignDocs.semester]
    val submittedAt = this[DesignDocs.submittedAt]
    val raw = this[DesignDocs.content]

    return when (this[DesignDocs.kind]) {
        DesignDocKind.INITIAL ->
            DesignDocEntry.Initial(id, semester, submittedAt, decodeDesignDoc(raw))

        DesignDocKind.RETURNING ->
            DesignDocEntry.Returning(id, semester, submittedAt, decodeReturningDoc(raw))
    }
}

/** Every doc [projectID] has filed, newest first. Must be inside a transaction. */
fun designDocsOf(projectID: Uuid): List<DesignDocEntry> =
    DesignDocs.selectAll()
        .where { DesignDocs.projectID eq projectID }
        .orderBy(DesignDocs.submittedAt, SortOrder.DESC)
        .map { it.toDesignDocEntry() }

/**
 * [projectID]'s doc for [semester], or null if it hasn't filed one. Must be inside a transaction.
 */
fun designDocFor(projectID: Uuid, semester: String): DesignDocEntry? =
    DesignDocs.selectAll()
        .where { (DesignDocs.projectID eq projectID) and (DesignDocs.semester eq semester) }
        .firstOrNull()
        ?.toDesignDocEntry()

/** Files a doc for [projectID]. Must be inside a transaction. */
fun insertDesignDoc(
    projectID: Uuid,
    semester: String,
    kind: DesignDocKind,
    content: String,
    submittedAt: Long = System.currentTimeMillis(),
): Uuid =
    DesignDocs.insert {
        it[DesignDocs.projectID] = projectID
        it[DesignDocs.semester] = semester.trim().take(32)
        it[DesignDocs.kind] = kind
        it[DesignDocs.content] = content
        it[DesignDocs.submittedAt] = submittedAt
    } get DesignDocs.id

/** Replaces the answers on an already-filed doc. Must be inside a transaction. */
fun updateDesignDocContent(docID: Uuid, content: String) {
    DesignDocs.update({ DesignDocs.id eq docID }) { it[DesignDocs.content] = content }
}
