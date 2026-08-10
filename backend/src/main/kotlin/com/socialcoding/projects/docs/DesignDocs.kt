package com.socialcoding.projects.docs

import com.socialcoding.api.db.SqlTable
import com.socialcoding.board.semesterLabel
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
import org.slf4j.LoggerFactory

/**
 * Every design doc a project has filed, one row per semester. A project's first row is its
 * [DesignDocKind.INITIAL] proposal; each semester it returns adds a [DesignDocKind.RETURNING]
 * check-in, so the rows read as the project's history.
 */
@SqlTable
object DesignDocs : Table("design_docs") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val projectID = uuid("project_id").references(Projects.id)

    /** The semester the doc was filed for, e.g. `"Fall 2026"`. */
    val semester = varchar("semester", 32)
    val kind = enumerationByName("kind", 16, DesignDocKind::class).default(DesignDocKind.INITIAL)

    /** The answers as JSON — a [DesignDocContent] or [ReturningDocContent] to match [kind]. */
    val content = text("content")
    val submittedAt = long("submitted_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // One doc per semester is the whole point; the routes upsert against this.
        uniqueIndex(projectID, semester)
    }
}

private val LOGGER = LoggerFactory.getLogger("DesignDocs")

// encodeDefaults keeps blank answers present in responses instead of omitted.
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Decodes stored proposal answers, falling back to blanks when the JSON can't be read. */
fun decodeDesignDoc(raw: String?): DesignDocContent =
    raw?.let { runCatching { json.decodeFromString<DesignDocContent>(it) }.getOrNull() }
        ?: DesignDocContent()

fun encodeDesignDoc(doc: DesignDocContent): String =
    json.encodeToString(DesignDocContent.serializer(), doc)

/** Decodes stored check-in answers, falling back to blanks when the JSON can't be read. */
fun decodeReturningDoc(raw: String?): ReturningDocContent =
    raw?.let { runCatching { json.decodeFromString<ReturningDocContent>(it) }.getOrNull() }
        ?: ReturningDocContent()

fun encodeReturningDoc(doc: ReturningDocContent): String =
    json.encodeToString(ReturningDocContent.serializer(), doc)

/** Reads a row as the entry clients see, decoding [DesignDocs.content] against its kind. */
fun ResultRow.toDesignDocEntry(): DesignDocEntry {
    val kind = this[DesignDocs.kind]
    val raw = this[DesignDocs.content]
    return DesignDocEntry(
        id = this[DesignDocs.id].toString(),
        semester = this[DesignDocs.semester],
        kind = kind,
        submittedAt = this[DesignDocs.submittedAt],
        initial = if (kind == DesignDocKind.INITIAL) decodeDesignDoc(raw) else null,
        returning = if (kind == DesignDocKind.RETURNING) decodeReturningDoc(raw) else null,
    )
}

/** Every doc [projectID] has filed, newest first. Must be inside a transaction. */
fun designDocsOf(projectID: Uuid): List<DesignDocEntry> =
    DesignDocs.selectAll()
        .where { DesignDocs.projectID eq projectID }
        .orderBy(DesignDocs.submittedAt, SortOrder.DESC)
        .map { it.toDesignDocEntry() }

/** [projectID]'s doc for [semester], or null if it hasn't filed one. Must be inside a transaction. */
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

/**
 * Moves the docs of projects that predate semester design docs onto [DesignDocs], filing each as
 * the project's [DesignDocKind.INITIAL] proposal under the semester it was submitted in.
 *
 * Runs at boot because the schema is created from the table objects rather than from migrations.
 * Projects that already have a row are skipped, so running it again does nothing. Must be inside a
 * transaction.
 */
fun backfillInitialDesignDocs(): Int {
    val filed = DesignDocs.selectAll().map { it[DesignDocs.projectID] }.toSet()
    val pending =
        Projects.selectAll()
            .map {
                Triple(it[Projects.id], it[Projects.legacyDesignDoc], it[Projects.submittedAt])
            }
            .filter { (id, doc, _) -> id !in filed && doc != null }

    pending.forEach { (id, doc, submittedAt) ->
        insertDesignDoc(
            projectID = id,
            semester = semesterLabel(submittedAt),
            kind = DesignDocKind.INITIAL,
            content = doc!!,
            submittedAt = submittedAt,
        )
    }

    if (pending.isNotEmpty()) {
        LOGGER.info("Backfilled {} project design docs into design_docs", pending.size)
    }
    return pending.size
}
