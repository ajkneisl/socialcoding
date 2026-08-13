package com.socialcoding

import com.socialcoding.api.Auth
import com.socialcoding.board.BoardSettings
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.EventOccurrences
import com.socialcoding.events.Events
import com.socialcoding.people.Role
import com.socialcoding.people.Users
import com.socialcoding.projects.ProjectLikes
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.Projects
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.models.ProjectStatus
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.toIdJson
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Lenient JSON for decoding responses in tests. */
val testJson = Json { ignoreUnknownKeys = true }

/** Decodes a response body into [T]. */
suspend inline fun <reified T> HttpResponse.decode(): T = testJson.decodeFromString(bodyAsText())

/** Builders for the rows tests need, plus session tokens for them. */
object Fixtures {
    private val counter = AtomicInteger()

    /** Inserts a user with a unique email and returns its id. */
    fun user(
        role: Role = Role.MEMBER,
        name: String = "User",
        email: String? = null,
        avatarUrl: String? = null,
        gradYear: Int? = null,
        listed: Boolean = true,
    ): Uuid = transaction {
        Users.insert {
            it[Users.email] = email ?: "user${counter.incrementAndGet()}@test.edu"
            it[Users.name] = name
            it[Users.role] = role
            it[Users.avatarUrl] = avatarUrl
            it[Users.gradYear] = gradYear
            it[Users.listed] = listed
            it[createdAt] = System.currentTimeMillis()
        } get Users.id
    }

    /** A session JWT for [id]. */
    fun token(id: Uuid): String = Auth.issue(id.toString())

    /**
     * Sets the board's presentation dates. Creating a project through the API needs these, since
     * its two required milestones are stamped from them.
     */
    fun presentationDates(mvp: String = "2026-10-01", final: String = "2026-12-01") {
        BoardSettings.set(BoardSettings.MVP_DATE, mvp)
        BoardSettings.set(BoardSettings.FINAL_DATE, final)
    }

    /**
     * Inserts a project owned by [owner] (who is implicitly its team lead) and returns its id.
     * [channel] sets the Discord channel id if the test needs one.
     */
    fun project(
        owner: Uuid,
        status: ProjectStatus = ProjectStatus.PENDING,
        active: Boolean = true,
        title: String = "Project",
        channel: String? = null,
        submittedAt: Long = System.currentTimeMillis(),
    ): Uuid = transaction {
        Projects.insert {
            it[Projects.title] = title
            it[description] = "A test project"
            it[ownerId] = owner
            it[Projects.status] = status
            it[Projects.active] = active
            it[discordChannelId] = channel
            it[Projects.submittedAt] = submittedAt
        } get Projects.id
    }

    /** Adds [user] to [project] with the given membership [status] (pending invite by default). */
    fun invite(project: Uuid, user: Uuid, status: MemberStatus = MemberStatus.PENDING) =
        transaction {
            ProjectMembers.insert {
                it[projectID] = project
                it[userID] = user
                it[ProjectMembers.status] = status
            }
        }

    /** Puts [user] on [project]'s team outright, skipping the invite. */
    fun member(project: Uuid, user: Uuid) = invite(project, user, MemberStatus.ACCEPTED)

    /** Sets [project]'s team lead, which projects don't have by default. */
    fun lead(project: Uuid, user: Uuid) = transaction {
        Projects.update({ Projects.id eq project }) { it[teamLeadId] = user }
    }

    /** Hearts [project] as [user]. */
    fun like(project: Uuid, user: Uuid) = transaction {
        ProjectLikes.insert {
            it[projectID] = project
            it[userID] = user
        }
    }

    /** Inserts a deliverable on [project] and returns its row id. */
    fun task(
        project: Uuid,
        name: String,
        dueDate: String = "",
        assignees: List<Uuid> = emptyList(),
        dependsOn: List<Long> = emptyList(),
        milestone: Boolean = false,
    ): Long = transaction {
        ProjectTasks.insert {
            it[projectID] = project
            it[ProjectTasks.name] = name
            it[assigneeIDs] = assignees.toIdJson()
            it[ProjectTasks.dueDate] = dueDate
            it[dependsOnIDs] = Json.encodeToString(dependsOn)
            it[ProjectTasks.milestone] = milestone
        } get ProjectTasks.id
    }

    /** Inserts an event authored by [author] and returns its id. */
    fun event(
        author: Uuid,
        title: String = "Event",
        startsAt: Long = System.currentTimeMillis(),
        attendance: Boolean = false,
        recurring: Boolean = false,
        announce: Boolean = false,
        announcedAt: Long? = null,
    ): Long = transaction {
        Events.insert {
            it[Events.title] = title
            it[summary] = "A summary"
            it[body] = ""
            it[Events.startsAt] = startsAt
            it[Events.attendance] = attendance
            it[Events.recurring] = recurring
            it[Events.announce] = announce
            it[Events.announcedAt] = announcedAt
            it[createdBy] = author
            it[createdAt] = 0
        } get Events.id
    }

    /** Checks [user] in to [event]. */
    fun attend(event: Long, user: Uuid, at: Long = 0) = transaction {
        EventAttendance.insert {
            it[eventID] = event
            it[userID] = user
            it[recordedAt] = at
        }
    }

    /** Banks a finished occurrence of a recurring [event], as the nightly roll does. */
    fun occurrence(event: Long, startsAt: Long, attendees: Long) = transaction {
        EventOccurrences.insert {
            it[eventID] = event
            it[EventOccurrences.startsAt] = startsAt
            it[EventOccurrences.attendees] = attendees
        }
    }
}
